package example.nucleus.utils.cipher

data class PoTokenResult(
    val playerRequestPoToken: String,
    val streamingDataPoToken: String,
)

class PoTokenException(message: String) : Exception(message)

/**
 * Genera poTokens WEB/WEB_REMIX (tokens de integridad BotGuard / WAA).
 *
 * La implementación real se encuentra en el source set JVM porque acuñar un poToken
 * requiere APIs de navegador reales (canvas/WebGL) que solo un Chromium embebido (JCEF,
 * incluido con el JetBrains Runtime) puede proporcionar. Un motor JS puro como GraalJS
 * puede ejecutar el intérprete de BotGuard lo suficiente para obtener la solicitud
 * del token de integridad, pero nunca completa `webPoSignalOutput`, por lo que no puede
 * acuñar el token final.
 */
expect object PoTokenGenerator {
    /**
 * Genera poTokens WEB/WEB_REMIX para solicitudes del cliente web.
 *
 * @return Un [PoTokenResult] con ambos tokens, o `null` si la generación falla.
 */
    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult?
}

/**
 * Orquestador de poTokens con cache de sesión y timeout: la fachada que consume el
 * player (ver [PoTokenGenerator] para la mecánica de generación por plataforma).
 */
expect object PoTokenManager {
    /**
     * Tokens para [videoId]: sesión ligada al /player + video ligado al pot= de la URL.
     *
     * @return El resultado, o `null` si la generación falló o excedió el timeout.
     */
    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult?

    /** Calienta el pipeline BotGuard para esconder el cold-start de la primera reproducción. */
    suspend fun prewarm(sessionId: String)

    /** Invalida la sesión cacheada y libera el motor. */
    suspend fun reset()
}
