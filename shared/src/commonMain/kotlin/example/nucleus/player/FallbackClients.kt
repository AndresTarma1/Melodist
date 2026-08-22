package example.nucleus.player

import com.metrolist.innertube.models.YouTubeClient

object FallbackClients {
    // WEB_REMIX is used ONLY for metadata (audioConfig/videoDetails); its stream is skipped
    // because, with poTokens disabled, web streams 403 and would needlessly load player.js.
    val mainClient: YouTubeClient = YouTubeClient.WEB_REMIX

    // WEB_REMIX sirve como cliente principal (metadatos + primer intento de stream con
    // PoToken + pot=). TVHTML5 entra como primer fallback explícito (requiere sesión; si
    // no está logueado se salta solo y se pasa a los clientes no-web).
    val streamFallbackClients: Array<YouTubeClient> = arrayOf(
        YouTubeClient.TVHTML5,
        YouTubeClient.VISIONOS,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.IOS,
        YouTubeClient.IPADOS,
        YouTubeClient.ANDROID_CREATOR,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.MOBILE,
    )
}
