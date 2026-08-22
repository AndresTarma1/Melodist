package example.nucleus.utils.cipher

import com.metrolist.innertube.YouTube
import java.util.logging.Logger

/**
 * Precalienta en segundo plano las piezas costosas del pipeline WEB de reproducción para
 * que el primer stream web no pague su cold-start (~20-25s el `prepare` de EJS, ~1-3s el
 * challenge de PoToken). Se invoca al arrancar la app; nada de esto debe bloquear la UI.
 */
object PlayerPipelineWarmup {
    private val log = Logger.getLogger("PlayerPipelineWarmup")
    private var done = false

    @Synchronized
    fun markDone() { done = true }

    /**
     * 1) Descarga el player.js (si no está cacheado) y pre-procesa el solucionador EJS.
     * 2) Calienta el sidecar de PoTokens si hay visitorData y binario disponible.
     */
    suspend fun warmup() {
        if (done) return
        // 1) Selver EJS (player.js + prepare): lo más lento la primera vez.
        runCatching {
            val (js, hash) = PlayerJsFetcher.getPlayerJs() ?: return@runCatching
            EjsCipherSolver.prepare(js, hash)
            log.info("Preparado EJS solver (hash=${hash.take(8)})")
        }.onFailure { log.warning("prewarm EJS falló: ${it.message}") }

        // 2) PoToken vía sidecar (si hay visitorData y el binario está presente).
        runCatching {
            val vd = YouTube.visitorData().getOrNull() ?: return@runCatching
            PoTokenManager.prewarm(vd)
            log.info("PoToken sidecar precalentado")
        }.onFailure { log.warning("prewarm PoToken falló: ${it.message}") }

        markDone()
    }
}
