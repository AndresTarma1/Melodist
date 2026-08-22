package example.nucleus.utils.cipher

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Pipeline completo PoTokenManager → rustypipe-botguard (sidecar).
 * Soft-pass si el binario no está presente (p.ej. CI en Linux): la generación de PoTokens
 * es un extra; la reproducción funciona sin ella vía clientes no-web.
 */
class PoTokenSidecarSmokeTest {

    @Test
    fun `manager generates potokens via sidecar`() = runBlocking {
        if (!RustyPipeBotGuardSidecar.isAvailable) {
            println("[pot] sidecar no disponible; saltando")
            return@runBlocking
        }
        val result = PoTokenManager.getWebClientPoToken(
            videoId = "dQw4w9WgXcQ",
            sessionId = "CgtfVHBR-SIDECAR-SMOKE",
        )
        assertNotNull(result, "[pot] PoTokenManager devolvió null")
        val s = result.playerRequestPoToken
        val v = result.streamingDataPoToken
        println("LEN s=${s.length} v=${v.length}")
        println("playerToken=$s")
        println("streamToken=$v")
        check(!s.contains("valid_until") && !s.contains(" ") && !s.contains('-') && !s.contains('_')) {
            "[pot] playerToken no es base64 estándar limpio: $s"
        }
        check(!v.contains(" ")) { "[pot] streamToken contiene espacios" }
        PoTokenManager.reset()
    }
}
