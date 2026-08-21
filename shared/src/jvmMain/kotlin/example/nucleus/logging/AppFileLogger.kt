package example.nucleus.logging

import example.nucleus.data.AppDirs
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Logging a disco para producción (Graal / .exe sin consola).
 *
 * - Siempre escribe **W/E** en [errorsLogFile] (caja negra ligera).
 * - Si [logToFile] está activo, escribe también I (y D/V si [verbose]) en el log diario.
 * - Redacta cookies / Authorization / firmas largas de URLs.
 * - Escritura asíncrona en un hilo daemon para no bloquear UI ni el ticker de mpv.
 */
object AppFileLogger {

    private val installed = AtomicBoolean(false)
    private val queue = ConcurrentLinkedQueue<String>()
    private val wake = Object()
    @Volatile private var running = false

    @Volatile var logToFile: Boolean = false
        private set
    @Volatile var verbose: Boolean = false
        private set

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private const val MAX_DAILY_BYTES = 8L * 1024 * 1024
    private const val MAX_ERROR_BYTES = 2L * 1024 * 1024
    private const val KEEP_DAILY_FILES = 5

    val logsDirectory: File
        get() = AppDirs.logsDir.also { it.mkdirs() }

    val errorsLogFile: File
        get() = File(logsDirectory, "app-errors.log")

    fun dailyLogFile(date: LocalDate = LocalDate.now()): File =
        File(logsDirectory, "paltasound-${dayFmt.format(date)}.log")

    /**
     * Registra el [Antilog] de archivo (idempotente).
     * Llamar desde [example.nucleus.bootstrap.AppEnvironment.initialize] tras crear dirs.
     */
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        logsDirectory.mkdirs()
        startWriter()
        Napier.base(FileAntilog())
        enqueueRaw("----- AppFileLogger started ${LocalDateTime.now()} -----")
    }

    fun applyPreferences(logToFile: Boolean, verbose: Boolean) {
        val was = this.logToFile
        this.logToFile = logToFile
        this.verbose = verbose
        if (logToFile && !was) {
            enqueueRaw("----- File logging ENABLED (verbose=$verbose) ${LocalDateTime.now()} -----")
            pruneOldDailyLogs()
        } else if (!logToFile && was) {
            enqueueRaw("----- File logging DISABLED ${LocalDateTime.now()} -----")
        }
    }

    fun clearLogs(): Boolean {
        return try {
            logsDirectory.listFiles()?.forEach { f ->
                if (f.isFile && (
                        f.name.startsWith("paltasound-") && f.name.endsWith(".log") ||
                            f.name == "app-errors.log" ||
                            f.name == "sysout.log" ||
                            f.name == "syserr.log"
                        )
                ) {
                    f.delete()
                }
            }
            enqueueRaw("----- Logs cleared ${LocalDateTime.now()} -----")
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun startWriter() {
        if (running) return
        running = true
        thread(name = "AppFileLogger", isDaemon = true, priority = Thread.NORM_PRIORITY - 1) {
            while (running) {
                val line = queue.poll()
                if (line == null) {
                    synchronized(wake) {
                        if (queue.isEmpty()) {
                            try {
                                wake.wait(500)
                            } catch (_: InterruptedException) {
                                // ignore
                            }
                        }
                    }
                    continue
                }
                writeLine(line)
            }
        }
    }

    private fun enqueueRaw(message: String) {
        queue.offer(message)
        synchronized(wake) { wake.notify() }
    }

    internal fun enqueueFormatted(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        val alwaysErrors = priority == LogLevel.WARNING || priority == LogLevel.ERROR || priority == LogLevel.ASSERT
        val includeInDaily = when {
            !logToFile -> false
            verbose -> true
            priority == LogLevel.INFO || alwaysErrors -> true
            else -> false
        }
        if (!alwaysErrors && !includeInDaily) return

        val ts = LocalDateTime.now().format(timeFmt)
        val level = when (priority) {
            LogLevel.VERBOSE -> "V"
            LogLevel.DEBUG -> "D"
            LogLevel.INFO -> "I"
            LogLevel.WARNING -> "W"
            LogLevel.ERROR -> "E"
            LogLevel.ASSERT -> "A"
        }
        val tagPart = tag?.takeIf { it.isNotBlank() } ?: "App"
        val body = buildString {
            append(redact(message.orEmpty()))
            if (throwable != null) {
                append(" | ")
                append(redact(throwable.toString()))
                val stack = throwable.stackTraceToString()
                if (stack.isNotBlank()) {
                    append('\n')
                    append(redact(stack).lineSequence().take(40).joinToString("\n"))
                }
            }
        }
        val line = "$ts $level/$tagPart: $body"
        // Prefijo de 2 chars + '|': [E|-][D|-]|payload  → p.ej. "ED|…", "E-|…", "-D|…"
        val errFlag = if (alwaysErrors) 'E' else '-'
        val dayFlag = if (includeInDaily) 'D' else '-'
        enqueueRaw("$errFlag$dayFlag|$line")
    }

    private fun writeLine(routed: String) {
        try {
            if (routed.length < 4 || routed[2] != '|') {
                // Marcadores de sistema (start/enable/clear): van a errors siempre y a daily si logging on.
                val payload = routed + "\n"
                appendFile(errorsLogFile, payload, MAX_ERROR_BYTES)
                if (logToFile) appendFile(dailyLogFile(), payload, MAX_DAILY_BYTES)
                return
            }
            val toErrors = routed[0] == 'E'
            val toDaily = routed[1] == 'D'
            val payload = routed.substring(3) + "\n"
            if (toErrors) appendFile(errorsLogFile, payload, MAX_ERROR_BYTES)
            if (toDaily) appendFile(dailyLogFile(), payload, MAX_DAILY_BYTES)
        } catch (_: Exception) {
            // nunca tumbar la app por logging
        }
    }

    private fun appendFile(file: File, payload: String, maxBytes: Long) {
        file.parentFile?.mkdirs()
        if (file.exists() && file.length() > maxBytes) {
            val bak = File(file.parentFile, file.name + ".1")
            bak.delete()
            file.renameTo(bak)
        }
        FileOutputStream(file, true).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { osw ->
                BufferedWriter(osw).use { bw ->
                    bw.write(payload)
                }
            }
        }
    }

    private fun pruneOldDailyLogs() {
        runCatching {
            val files = logsDirectory.listFiles { f ->
                f.isFile && f.name.startsWith("paltasound-") && f.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() } ?: return
            files.drop(KEEP_DAILY_FILES).forEach { it.delete() }
        }
    }

    /** Quita secretos obvios de líneas de log. */
    fun redact(input: String): String {
        if (input.isEmpty()) return input
        var s = input
        s = COOKIE_HEADER_REGEX.replace(s) { mr ->
            val key = mr.groupValues[1]
            "$key=***"
        }
        s = AUTH_REGEX.replace(s, "Authorization: ***")
        s = SAPISID_REGEX.replace(s, "$1***")
        s = QUERY_SECRET_REGEX.replace(s) { mr ->
            "${mr.groupValues[1]}=***"
        }
        if (s.length > 4000) s = s.take(4000) + "…(truncated)"
        return s
    }

    private val COOKIE_HEADER_REGEX =
        Regex("""(?i)((?:cookie|set-cookie)\s*[:=]\s*)([^\s;]+(?:;\s*[^\s;]+)*)""")
    private val AUTH_REGEX =
        Regex("""(?i)Authorization\s*:\s*\S+""")
    private val SAPISID_REGEX =
        Regex("""(?i)\b(SAPISID|HSID|SSID|APISID|SID|LOGIN_INFO|__Secure-[A-Za-z0-9_-]+)=([^\s;]+)""")
    private val QUERY_SECRET_REGEX =
        Regex(
            """(?i)([?&](?:signature|sig|lsig|expire|pot|token|access_token|refresh_token)=)([^&\s]+)""",
        )

    private class FileAntilog : Antilog() {
        override fun performLog(
            priority: LogLevel,
            tag: String?,
            throwable: Throwable?,
            message: String?,
        ) {
            enqueueFormatted(priority, tag, throwable, message)
        }
    }
}
