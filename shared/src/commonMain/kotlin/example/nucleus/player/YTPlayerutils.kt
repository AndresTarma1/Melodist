package example.nucleus.player

import example.nucleus.data.repository.AudioQuality
import example.nucleus.data.repository.VideoQuality
import example.nucleus.utils.cipher.PoTokenManager
import example.nucleus.utils.cipher.PoTokenResult
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.response.PlayerResponse
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YTPlayerutils {
    /**
     * Resuelve los datos de reproducción y la URL del stream para un video con la calidad de audio especificada.
     *
     * Si [videoQuality] no es null, además intenta resolver un formato de video (solo-video, acotado a
     * [VideoQuality.maxHeight]) en la misma cascada de clientes. El video es best-effort: si ningún
     * cliente lo sirve, la reproducción continúa solo con audio.
     *
     * @return Un `Result` que contiene los datos de reproducción resueltos y la URL del stream.
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality = AudioQuality.NORMAL,
        videoQuality: VideoQuality? = null,
    ): Result<PlaybackData> = runCatching {

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Napier.i { "Signature timestamp for $videoId: $signatureTimestamp" }

        val isLoggedIn = YouTube.cookie != null

        if (YouTube.visitorData == null) {
            YouTube.visitorData().onSuccess { vd ->
                YouTube.visitorData = vd
                Napier.i("visitorData obtained: ${vd.take(8)}...")
            }.onFailure {
                Napier.w("Failed to fetch visitorData: ${it.message}")
            }
        }

        // PoToken vía sidecar rustypipe-botguard (ver RustyPipeBotGuardSidecar): el runtime
        // de BotGuard corre en un proceso externo con entorno JSDOM (desde 2026 el minter
        // WebPO no se entrega a runtimes embebidos con shims stub).
        //  - playerRequestPoToken va en el body del /player del cliente principal.
        //  - streamingDataPoToken se agrega como pot= a las URLs de los clientes web,
        //    lo que re-habilita sus formatos de solo-audio de alta calidad.
        // Si visitorData aún no está listo o la generación falla/excede el timeout,
        // seguimos sin poToken: los clientes no-web + yt-dlp cubren el playback.
        val poTokenResult: PoTokenResult? = YouTube.visitorData?.let { vd ->
            PoTokenManager.getWebClientPoToken(videoId, vd)
        }

        val mainPlayerResponse =
            YouTube.player(
                videoId,
                playlistId,
                FallbackClients.mainClient,
                signatureTimestamp,
                poTokenResult?.playerRequestPoToken,
            ).getOrThrow()
        Napier.d("Resolving playback stream for $videoId with quality=$audioQuality videoQuality=$videoQuality signatureTimestamp=$signatureTimestamp")
        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking

        var chosenFormat: PlayerResponse.StreamingData.Format? = null
        var chosenStreamUrl: String? = null
        var chosenExpires: Int? = null
        var chosenVideoFormat: PlayerResponse.StreamingData.Format? = null
        var chosenVideoUrl: String? = null
        var chosenResponse: PlayerResponse? = null

        for (clientIndex in (-1 until FallbackClients.streamFallbackClients.size)) {
            val streamPlayerResponse: PlayerResponse?
            val client: YouTubeClient?
            if (clientIndex == -1) {
                streamPlayerResponse = mainPlayerResponse
                client = null
            } else {
                val fallbackClient = FallbackClients.streamFallbackClients[clientIndex]
                if (fallbackClient.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    Napier.d("Skipping playback client ${fallbackClient.clientName} for $videoId because login is required")
                    continue
                }
                val clientPoToken =
                    if (fallbackClient.useWebPoTokens) poTokenResult?.playerRequestPoToken else null
                val result = YouTube.player(videoId, playlistId, fallbackClient, signatureTimestamp, clientPoToken)
                streamPlayerResponse = result.getOrNull()
                if (streamPlayerResponse == null) {
                    Napier.w("Player response null for ${fallbackClient.clientName} for $videoId: ${result.exceptionOrNull()?.message}")
                }
                client = fallbackClient
            }

            // Sin poToken, los clientes web (useWebPoTokens) dan error 403 en el CDN y cargarían
            // player.js para sig/n: se omite su resolución de stream. CON poToken válido pasan a
            // ser candidatos (formatos de solo-audio de alta calidad); WEB_REMIX sigue sirviendo
            // como fuente de metadatos arriba.
            if ((client ?: FallbackClients.mainClient).useWebPoTokens && poTokenResult == null) {
                continue
            }

            if (streamPlayerResponse?.playabilityStatus?.status != "OK") {
                if (streamPlayerResponse != null) {
                    Napier.w(
                        "Playback client response not OK for $videoId: status=${streamPlayerResponse.playabilityStatus.status}, reason=${streamPlayerResponse.playabilityStatus.reason}",
                    )
                }
                continue
            }

            val newPipeResponse = withContext(Dispatchers.IO) {
                YouTube.newPipePlayer(videoId, streamPlayerResponse)
            }
            val responseToUse = newPipeResponse ?: streamPlayerResponse
            val clientName = client?.clientName ?: FallbackClients.mainClient.clientName
            val effectiveClient = client ?: FallbackClients.mainClient
            Napier.d("Trying playback client $clientName for $videoId; newPipeResponse=${newPipeResponse != null}")

            // Candidatos de este cliente: muxed (video+audio juntos) vs separados.
            // Se resuelven ambos y se elige el que respete mejor la resolución pedida:
            // muxed solo si su altura >= separada (no degradar 1080p separado a 720p muxed).
            var candidateMuxedFormat: PlayerResponse.StreamingData.Format? = null
            var candidateMuxedUrl: String? = null
            if (videoQuality != null) {
                val mf = FormatSelector.findMuxedFormat(responseToUse, videoQuality.maxHeight)
                if (mf != null) {
                    val url = resolveStreamUrlCached(
                        mf, videoId, responseToUse, clientName, effectiveClient,
                        poTokenResult?.streamingDataPoToken, isVideo = false,
                    )
                    if (url != null && StreamUrlResolver.validate(url)) {
                        candidateMuxedFormat = mf
                        candidateMuxedUrl = url
                        Napier.d("Candidate muxed for $videoId client $clientName itag=${mf.itag} height=${mf.height}")
                    }
                }
            }
            var candidateAudioFormat: PlayerResponse.StreamingData.Format? = null
            var candidateAudioUrl: String? = null
            if (chosenStreamUrl == null) {
                val af = FormatSelector.findFormat(responseToUse, audioQuality)
                if (af == null) {
                    Napier.w("No audio format found for $videoId using client $clientName")
                } else {
                    val url = resolveStreamUrlCached(
                        af, videoId, responseToUse, clientName, effectiveClient,
                        poTokenResult?.streamingDataPoToken, isVideo = false,
                    )
                    if (url != null && StreamUrlResolver.validate(url)) {
                        candidateAudioFormat = af
                        candidateAudioUrl = url
                    } else {
                        Napier.w("Stream URL resolution/validation failed for $videoId using client $clientName itag=${af.itag}; trying fallback client")
                    }
                }
            }
            var candidateVideoFormat: PlayerResponse.StreamingData.Format? = null
            var candidateVideoUrl: String? = null
            if (videoQuality != null && chosenVideoUrl == null) {
                val vf = FormatSelector.findVideoFormat(responseToUse, videoQuality.maxHeight)
                if (vf != null) {
                    val url = resolveStreamUrlCached(
                        vf, videoId, responseToUse, clientName, effectiveClient,
                        poTokenResult?.streamingDataPoToken, isVideo = true,
                    )
                    if (url != null && StreamUrlResolver.validate(url)) {
                        candidateVideoFormat = vf
                        candidateVideoUrl = url
                        Napier.d("Candidate video for $videoId client $clientName itag=${vf.itag} height=${vf.height}")
                    }
                }
            }
            val muxedH = candidateMuxedFormat?.height ?: 0
            val separateH = candidateVideoFormat?.height ?: 0
            val useMuxed = candidateMuxedUrl != null && (candidateVideoFormat == null || muxedH >= separateH)
            if (useMuxed && candidateMuxedUrl != null) {
                if (chosenStreamUrl == null) {
                    chosenFormat = candidateMuxedFormat
                    chosenStreamUrl = candidateMuxedUrl
                    chosenExpires = streamPlayerResponse.streamingData?.expiresInSeconds
                    chosenResponse = streamPlayerResponse
                    Napier.d("Resolved muxed video+audio for $videoId using client $clientName itag=${candidateMuxedFormat?.itag} height=${candidateMuxedFormat?.height}")
                }
                if (chosenVideoUrl == null) {
                    chosenVideoFormat = candidateMuxedFormat
                    chosenVideoUrl = candidateMuxedUrl
                }
            } else {
                if (chosenStreamUrl == null && candidateAudioUrl != null) {
                    chosenFormat = candidateAudioFormat
                    chosenStreamUrl = candidateAudioUrl
                    chosenExpires = streamPlayerResponse.streamingData?.expiresInSeconds
                    chosenResponse = streamPlayerResponse
                    if (candidateAudioFormat != null) Napier.d("Resolved stream for $videoId using client $clientName itag=${candidateAudioFormat.itag}")
                }
                if (chosenVideoUrl == null && candidateVideoUrl != null) {
                    chosenVideoFormat = candidateVideoFormat
                    chosenVideoUrl = candidateVideoUrl
                }
            }

            val audioDone = chosenStreamUrl != null
            val videoDone = videoQuality == null || chosenVideoUrl != null
            if (audioDone && videoDone) break
        }

        if (chosenResponse == null) {
            throw Exception("Bad stream player response")
        }
        if (chosenResponse.playabilityStatus.status != "OK") {
            throw Error(chosenResponse.playabilityStatus.reason ?: "Unknown error")
        }
        var streamExpiresInSeconds = chosenExpires
        if (streamExpiresInSeconds == null) {
            Napier.w("Missing stream expiry for $videoId; defaulting to 6h")
            streamExpiresInSeconds = 21600 // 6 hours default
        }
        if (chosenFormat == null) throw Exception("Could not find format")
        if (chosenStreamUrl == null) throw Exception("Could not find stream url")

        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            chosenFormat,
            chosenStreamUrl,
            streamExpiresInSeconds,
            chosenVideoFormat,
            chosenVideoUrl,
        )
    }

    /**
     * Resuelve (con caché) la URL de stream de un formato dentro de la cascada de clientes,
     * aplicando transformación `n` y `pot=` igual que la resolución de audio histórica.
     * Las claves de caché de video llevan sufijo `|video` para no pisar las de audio.
     */
    private suspend fun resolveStreamUrlCached(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        responseToUse: PlayerResponse,
        clientName: String,
        effectiveClient: YouTubeClient,
        streamingPot: String?,
        isVideo: Boolean,
    ): String? {
        val cacheKey = if (isVideo) {
            "$videoId|${format.itag}|$clientName|video"
        } else {
            "$videoId|${format.itag}|$clientName"
        }
        var url = StreamCache.get(cacheKey)

        if (url == null) {
            url = withContext(Dispatchers.IO) {
                StreamUrlResolver.resolveUrl(format, videoId, responseToUse, isVideo = isVideo)
            }
            if (url != null) {
                val ttl = minOf(
                    responseToUse.streamingData?.expiresInSeconds?.times(1000L) ?: 300_000L,
                    300_000L,
                )
                StreamCache.put(cacheKey, url, ttl)
            }
        }

        if (url == null) {
            Napier.w("No stream URL found for $videoId itag=${format.itag} using client $clientName (video=$isVideo)")
            return null
        }

        // Transformación de n solo para clientes web (igual que Metrolist). Los clientes no-web (VISIONOS,
        // IOS, ANDROID_VR, ...) obtienen URLs cuya n está ausente o ya es manejada por NewPipe;
        // transformarlas con la función n del reproductor web las rompería.
        val needsNTransform = effectiveClient.useWebPoTokens ||
            effectiveClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")
        if (needsNTransform) {
            url = StreamUrlResolver.applyNTransform(url)
        }
        if (effectiveClient.useWebPoTokens && streamingPot != null) {
            val separator = if (url.contains('?')) "&" else "?"
            // El token es base64url ([A-Za-z0-9_-]); solo el padding '=' requiere escape.
            url = url + separator + "pot=" + streamingPot.replace("=", "%3D")
        }
        return url
    }

    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        return YouTube.player(videoId, playlistId, client = FallbackClients.mainClient)
    }

    /**
     * `sts` real del player.js de YouTube asociado al [videoId], extraído del mismo base.js
     * que usa el solucionador EJS para sig/n ([PlayerJsFetcher]). Es el canal que usa Metrolist
     * (leer `signatureTimestamp` del player.js), funciona sin sesión y es estable frente a
     * cambios de layout. Si no se puede obtener, devuelve null y [InnerTube] omite `playbackContext`
     * (mejor que inventar un valor tipo "días desde epoch").
     */
    private suspend fun getSignatureTimestampOrNull(videoId: String): Int? =
        example.nucleus.utils.cipher.PlayerJsFetcher.getSignatureTimestamp()

    /**
     * Invalida las URLs de stream en caché para un video.
     *
     * @param videoId El ID del video.
     */
    suspend fun forceRefreshForVideo(videoId: String) {
        StreamCache.invalidateForVideo(videoId)
    }
}
