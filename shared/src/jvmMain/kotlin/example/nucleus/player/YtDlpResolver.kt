package example.nucleus.player

import example.nucleus.data.repository.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Resolvedor de último recurso que invoca **yt-dlp** para obtener una URL de audio directa.
 *
 * El pipeline interno de MusicPlayer (WEB_REMIX + EJS sig/n + JCEF poToken) cubre los videos
 * normales, pero algunos videos "difíciles" dan error 403 en todos los clientes que podemos
 * construir (necesitan el flujo web_embedded cifrado de yt-dlp). yt-dlp maneja esos casos,
 * por lo que recurrimos a él cuando mpv no logra iniciar la reproducción del stream resuelto.
 *
 * La resolución solo se invoca al fallar la reproducción, por lo que su costo de inicio de ~1-3s
 * nunca afecta el camino común (funcional).
 */
object YtDlpResolver {
    private val log = Logger.getLogger("YtDlpResolver")

    // Videos que el pipeline interno no pudo reproducir en esta sesión (todos los clientes 403).
    // Recordarlos permite al llamador omitir el ciclo lento de proceso-interno + fallo-de-mpv en
    // reproducciones repetidas y ir directo a yt-dlp. Solo durante la sesión (se limpia al reiniciar,
    // por si YouTube/los clientes cambian).
    private val knownYtDlpOnly = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun markNeedsYtDlp(videoId: String) { knownYtDlpOnly.add(videoId) }
    fun needsYtDlp(videoId: String): Boolean = knownYtDlpOnly.contains(videoId)

    /**
     * Devuelve un selector de formato de yt-dlp para la calidad de audio especificada.
     *
     * @param quality El nivel de calidad de audio deseado.
     * @return Un selector de formato `-f` de yt-dlp.
     */
    // El fallback final /best (o /worst) es obligatorio: con sesión iniciada, YouTube exige un
    // GVS PO Token para los formatos de solo-audio y yt-dlp los omite; sin ese fallback la
    // selección queda vacía ("Requested format is not available") aunque el itag 18 muxed
    // (con audio) sí sea servible.
    private fun formatSelector(quality: AudioQuality): String = when (quality) {
        AudioQuality.LOW -> "worstaudio/bestaudio[abr<=70]/bestaudio/worst/best"
        AudioQuality.NORMAL -> "bestaudio[abr<=128]/bestaudio/best"
        AudioQuality.HIGH -> "bestaudio/best"
    }

    /**
     * Selector para el modo video: prioriza separados (`bv*+ba`) acotado a [maxHeight]
     * para respetar la calidad pedida; si no hay par separado a esa altura, cae a muxed
     * (`b`) del mismo tope. El fallback final es obligatorio (igual que en audio): sin
     * él, la selección puede quedar vacía. Nota: el resolver en-proceso (`YTPlayerutils`)
     * hace la comparación muxed vs separado por altura y prefiere muxed solo cuando
     * no degrada (ej. no baja 1080p separado a 720p muxed).
     */
    private fun videoFormatSelector(maxHeight: Int): String =
        "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]/bv*+ba/b"

    /**
     * Resuelve un par (video, audio) de URLs directas vía yt-dlp para el modo video.
     * Con `bv*+ba` yt-dlp imprime dos líneas (video y audio); con el fallback muxed una sola
     * (se usa como ambas).
     *
     * @return Pair(videoUrl, audioUrl) o null si yt-dlp no está disponible o falla.
     */
    suspend fun resolveVideoUrls(
        videoId: String,
        maxHeight: Int = 720,
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        val exe = ytDlpPath ?: run {
            log.warning("yt-dlp not found (not bundled and not on PATH); cannot fall back")
            return@withContext null
        }
        val watchUrl = "https://music.youtube.com/watch?v=$videoId"
        try {
            log.info("yt-dlp fallback: resolving video+audio $videoId (maxHeight=$maxHeight) via $exe")
            val args = buildList {
                add(exe); add("--no-warnings"); add("--no-playlist")
                add("-f"); add(videoFormatSelector(maxHeight))
                cookiesFile()?.let { add("--cookies"); add(it.absolutePath) }
                add("-g"); add(watchUrl)
            }
            val proc = ProcessBuilder(args).start()

            val stdout = proc.inputStream.bufferedReader().readText()
            val stderr = proc.errorStream.bufferedReader().readText()
            if (!proc.waitFor(45, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                log.warning("yt-dlp timed out for $videoId")
                return@withContext null
            }
            val urls = stdout.lineSequence().map { it.trim() }.filter { it.startsWith("http") }.toList()
            when {
                urls.size >= 2 -> {
                    log.info("yt-dlp fallback resolved video+audio for $videoId")
                    urls[0] to urls[1]
                }
                urls.size == 1 -> {
                    // Formato muxed (una sola URL con audio y video): se usa para ambos.
                    log.info("yt-dlp fallback resolved muxed stream for $videoId")
                    urls[0] to urls[0]
                }
                else -> {
                    log.warning("yt-dlp returned no URL for $videoId: ${stderr.take(300)}")
                    null
                }
            }
        } catch (e: Exception) {
            log.warning("yt-dlp invocation failed for $videoId: ${e.message}")
            null
        }
    }

    /**
     * Resuelve una URL de stream de audio directa para el ID de video dado.
     *
     * Requiere que yt-dlp esté disponible como ejecutable incluido o en el PATH del sistema.
     *
     * @return Una URL de stream de audio directa, o `null` si yt-dlp no está disponible o la resolución falla.
     */
    suspend fun resolveAudioUrl(
        videoId: String,
        quality: AudioQuality = AudioQuality.NORMAL,
    ): String? = withContext(Dispatchers.IO) {
        val exe = ytDlpPath ?: run {
            log.warning("yt-dlp not found (not bundled and not on PATH); cannot fall back")
            return@withContext null
        }
        val watchUrl = "https://music.youtube.com/watch?v=$videoId"
        try {
            log.info("yt-dlp fallback: resolving $videoId (quality=$quality) via $exe")
            val args = buildList {
                add(exe); add("--no-warnings"); add("--no-playlist")
                add("-f"); add(formatSelector(quality))
                // Pasar la cookie de sesión para que los videos con restricción de edad / que
                // requieren inicio de sesión puedan resolverse. Debe ser un archivo cookies.txt
                // de formato Netscape: un header-cookie solo tiene alcance en el host de descarga,
                // no en las llamadas a la API de youtube.com que yt-dlp realiza durante la extracción.
                cookiesFile()?.let { add("--cookies"); add(it.absolutePath) }
                add("-g"); add(watchUrl)
            }
            val proc = ProcessBuilder(args).start()

            val stdout = proc.inputStream.bufferedReader().readText()
            val stderr = proc.errorStream.bufferedReader().readText()
            if (!proc.waitFor(45, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                log.warning("yt-dlp timed out for $videoId")
                return@withContext null
            }
            val url = stdout.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("http") }
            if (url == null) {
                log.warning("yt-dlp returned no URL for $videoId: ${stderr.take(300)}")
            } else {
                log.info("yt-dlp fallback resolved $videoId (urlLen=${url.length})")
            }
            url
        } catch (e: Exception) {
            log.warning("yt-dlp invocation failed for $videoId: ${e.message}")
            null
        }
    }

    private var cachedCookiesFile: File? = null
    private var cachedCookieValue: String? = null

    /**
     * Escribe la cookie de sesión en un archivo Netscape `cookies.txt` (el formato que yt-dlp
     * aplica a las llamadas de extracción de youtube.com), tanto para `.youtube.com` como para
     * `.google.com`. Se cachea hasta que la cookie cambia. Devuelve null si no hay sesión activa.
     */
    private fun cookiesFile(): File? {
        val cookie = com.metrolist.innertube.YouTube.cookie?.takeIf { it.isNotBlank() } ?: return null
        cachedCookiesFile?.let { if (cachedCookieValue == cookie && it.exists()) return it }
        return try {
            val dir = File(example.nucleus.platform.AppPaths.tmpDir).apply { mkdirs() }
            val file = File(dir, "ytdlp-cookies.txt")
            val sb = StringBuilder("# Netscape HTTP Cookie File\n")
            cookie.split(';').forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) return@forEach
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isEmpty()) return@forEach
                for (domain in listOf(".youtube.com", ".google.com")) {
                    // dominio \t includeSubdomains \t path \t secure \t expiry \t name \t value
                    sb.append(domain).append("\tTRUE\t/\tTRUE\t2147483647\t").append(name).append('\t').append(value).append('\n')
                }
            }
            file.writeText(sb.toString())
            cachedCookiesFile = file
            cachedCookieValue = cookie
            file
        } catch (e: Exception) {
            log.warning("Failed to write yt-dlp cookies file: ${e.message}")
            null
        }
    }

    val isAvailable: Boolean get() = ytDlpPath != null

    /** El binario incluido (junto a libmpv) tiene prioridad sobre una instalación en el PATH del sistema. */
    private val ytDlpPath: String? by lazy { locateYtDlp() }

    /**
     * Determina la ruta absoluta al ejecutable de yt-dlp.
     * 
     * Prefiere los ejecutables incluidos en directorios de recursos conocidos, luego recurre a
     * una búsqueda en todo el sistema.
     *
     * @return La ruta absoluta a yt-dlp, o `null` si no se encuentra.
     */
    private fun locateYtDlp(): String? {
        val userDir = File(System.getProperty("user.dir"))
        val rootDir = userDir.parentFile ?: userDir
        val resProp = System.getProperty("compose.application.resources.dir")
        val candidates = buildList {
            resProp?.let { add(File(it, "yt-dlp.exe")); add(File(File(it, "windows"), "yt-dlp.exe")) }
            add(File(userDir, "resources/yt-dlp.exe"))
            add(File(userDir, "mpv-resources/windows/yt-dlp.exe"))
            add(File(rootDir, "mpv-resources/windows/yt-dlp.exe"))
        }
        candidates.firstOrNull { it.exists() }?.let {
            log.info("Using bundled yt-dlp: ${it.absolutePath}")
            return it.absolutePath
        }
        // Recurrir a una instalación del sistema, resolviendo su ruta absoluta con where/which.
        return resolveOnPath("yt-dlp")?.also { log.info("Using system yt-dlp: $it") }
    }

    /**
     * Localiza un ejecutable en el PATH del sistema.
     *
     * @param name El nombre del ejecutable a localizar.
     * @return La ruta absoluta al ejecutable si se encuentra, `null` en caso contrario.
     */
    private fun resolveOnPath(name: String): String? = try {
        val which = if (System.getProperty("os.name").startsWith("Windows", true)) "where" else "which"
        val proc = ProcessBuilder(which, name).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0) {
            out.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        } else null
    } catch (e: Exception) {
        // `where`/`which` no disponible — asumir que no está instalado.
        null
    }
}
