package example.nucleus.utils.cipher

import example.nucleus.platform.AppPaths
import io.github.aakira.napier.Napier
import java.io.File
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * Desofuscación de firma ("sig") y limitación ("n") respaldada por el solucionador
 * yt-dlp/ejs (`assets/solver/yt.solver.core.js`, construido desde https://github.com/yt-dlp/ejs).
 *
 * A diferencia del [FunctionNameExtractor] basado en expresiones regulares, este analiza
 * player.js con meriyah/astring y extrae las funciones de transformación de forma estructural,
 * por lo que sobrevive a los frecuentes cambios de ofuscación del reproductor de YouTube
 * sin nombres de función hardcodeados ni configuraciones por hash.
 *
 * Protocolo (validado contra el reproductor ae0b654c):
 *   main({type:'player', player:<js>, output_preprocessed:true, requests:[...]})
 *     -> {responses:[{type:'result', data:{<challenge>:<solved>}}], preprocessed_player:<string>}
 * Preprocesamos una vez por hash del reproductor y almacenamos en caché `preprocessed_player`
 * en el ámbito global del JS, luego lo reutilizamos para cada resolución de sig/n para evitar
 * re-analizar el player.js de ~2.7 MB.
 */
object EjsCipherSolver {
    private val engine: ScriptEngine by lazy {
        ScriptEngineManager().getEngineByName("graal.js")
            ?: throw CipherException("GraalJS engine not available")
    }

    private var loaded = false
    private var preparedHash: String? = null

    /**
     * Inicializa el motor del solucionador de JavaScript con los recursos necesarios en la primera llamada.
     */
    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        Napier.i("[EJS] Loading solver (meriyah + astring + yt.solver.core)...")
        // Los paquetes UMD recurren a `globalThis.<name>` cuando no existen module/define.
        engine.eval("var globalThis = this; var window = this; var self = this;")
        engine.eval(readAsset("solver/meriyah.js"))
        engine.eval(readAsset("solver/astring.js"))
        engine.eval(readAsset("solver/yt.solver.core.js")) // defines `var jsc = main`
        engine.eval("this.__jsc = jsc;")
        loaded = true
        Napier.i("[EJS] Solver loaded")
    }

    /** Analiza player.js y almacena en caché su forma preprocesada (sin efecto si el mismo hash ya está preparado). */
    @Synchronized
    fun prepare(playerJs: String, hash: String) {
        ensureLoaded()
        if (preparedHash == hash) return

        // Si ya pre-procesamos este player.js en una ejecución anterior, cargamos el resultado
        // desde disco y nos ahorramos el parse de meriyah/astring (~20s por sesión en intérprete).
        val cacheFile = ppCacheFile(hash)
        if (cacheFile.exists()) {
            val cachedPp = runCatching { cacheFile.readText() }.getOrNull()
            if (!cachedPp.isNullOrEmpty()) {
                engine.put("__ppCached", cachedPp)
                engine.eval("this.__pp = __ppCached;")
                preparedHash = hash
                Napier.i("[EJS] Preprocessed player cargado desde disco (hash=${hash.take(8)}, ppLen=${cachedPp.length})")
                return
            }
        }

        engine.put("__playerJs", playerJs)
        val status = engine.eval(
            """
            (function() {
                try {
                    var out = this.__jsc({ type: 'player', player: __playerJs, output_preprocessed: true, requests: [] });
                    this.__pp = out.preprocessed_player;
                    return (typeof this.__pp === 'string') ? 'OK' : 'ERR:no preprocessed_player';
                } catch (e) { return 'ERR:' + ((e && e.message) || e); }
            })();
            """.trimIndent()
        ).toString()
        if (status != "OK") throw CipherException("EJS prepare failed: $status")

        // Persistimos el player pre-procesado para saltarnos el parse en próximas ejecuciones.
        runCatching { cacheFile.writeText(engine.eval("this.__pp").toString()) }
            .onFailure { Napier.w("[EJS] No se pudo persistir el preprocessed player: ${it.message}") }

        preparedHash = hash
        Napier.i("[EJS] Prepared player hash=$hash")
    }

    private fun ppCacheFile(hash: String): File {
        val base = File(AppPaths.cacheDir, "cipher_cache")
        if (!base.exists()) base.mkdirs()
        return File(base, "pp_$hash.txt")
    }

    /**
     * Resuelve un desafío de cifrado usando la representación del reproductor previamente preparada.
     *
     * @param type El tipo del desafío, típicamente "sig" o "n".
     * @return El valor resuelto como cadena, o null si la resolución falla o el reproductor no fue preparado.
     */
    @Synchronized
    fun solve(type: String, challenge: String): String? {
        if (preparedHash == null) {
            Napier.e("[EJS] solve called before prepare()")
            return null
        }
        engine.put("__type", type)
        engine.put("__challenge", challenge)
        val raw = engine.eval(
            """
            (function() {
                try {
                    var out = this.__jsc({ preprocessed_player: this.__pp, requests: [ { type: __type, challenges: [ __challenge ] } ] });
                    var r = out.responses[0];
                    if (!r || r.type !== 'result') return 'ERR:' + (r ? r.error : 'no response');
                    return 'OK:' + r.data[__challenge];
                } catch (e) { return 'ERR:' + ((e && e.message) || e); }
            })();
            """.trimIndent()
        ).toString()
        return if (raw.startsWith("OK:")) {
            raw.substring(3)
        } else {
            Napier.e("[EJS] solve $type failed: $raw")
            null
        }
    }

    /**
     * Carga recursos de texto desde el classpath.
     *
     * @param path La ruta relativa del recurso.
     * @return El contenido de texto del recurso.
     * @throws CipherException Si el recurso no se encuentra.
     */
    private fun readAsset(path: String): String {
        val fullPath = "example/nucleus/utils/cipher/assets/$path"
        val resource = javaClass.classLoader?.getResource(fullPath)
            ?: throw CipherException("Asset not found: $fullPath")
        return resource.readText()
    }
}
