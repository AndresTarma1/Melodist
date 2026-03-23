package com.example.melodist.player

import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.response.PlayerResponse
import java.util.logging.Logger

/**
 * Resolved stream info including URL and format metadata.
 */
data class ResolvedStream(
    val url: String,
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long?,
    val sampleRate: Int?,
    val loudnessDb: Double?,
    val codecs: String,
    val expiresInSeconds: Long?,
)

/**
 * Audio quality selection for stream resolution.
 */
enum class StreamQuality { LOW, NORMAL, HIGH }

/**
 * Custom exception for age-restricted content.
 */
class AgeRestrictedException(message: String) : Exception(message)

/**
 * Resolves a video ID into a playable audio stream URL.
 *
 * Strategy (mirrors Metrolist's YTPlayerUtils):
 *   1. MAIN client: WEB_REMIX (+ NewPipe deobfuscation + per-client signatureTimestamp)
 *   2. FALLBACK clients in order (each gets its own fresh signatureTimestamp):
 *      TVHTML5_SIMPLY_EMBEDDED_PLAYER, TVHTML5, ANDROID_VR_1_43_32, ANDROID_VR_1_61_48,
 *      ANDROID_CREATOR, IPADOS, ANDROID_VR_NO_AUTH, MOBILE, IOS, WEB, WEB_CREATOR
 */
class AudioStreamResolver {

    private val log = Logger.getLogger("AudioStreamResolver")

    /** Current quality preference. Updated by the caller (e.g. PlayerViewModel). */
    var quality: StreamQuality = StreamQuality.HIGH

    private val fallbackClients: List<YouTubeClient> = listOf(
        YouTubeClient.IOS,
        YouTubeClient.WEB_REMIX,
        YouTubeClient.TVHTML5,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_CREATOR,
        YouTubeClient.IPADOS,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.MOBILE,
        YouTubeClient.WEB,
        YouTubeClient.WEB_CREATOR,
    )

    /**
     * Returns the best audio stream URL for [videoId], or `null` if all clients fail.
     */
    suspend fun resolveAudioUrl(videoId: String): String? {
        return resolveAudioStream(videoId)?.url
    }

    /**
     * Returns full stream info (URL + format metadata) for [videoId], or `null` if all clients fail.
     * Used by DownloadService to get contentLength, itag, mimeType for chunked downloads.
     */
    suspend fun resolveAudioStream(videoId: String): ResolvedStream? {
        for (client in fallbackClients) {
            try {
                val signatureTimestamp = if (client.useSignatureTimestamp) {
                    NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
                } else {
                    null
                }

                val response = YouTube.player(
                    videoId = videoId,
                    client = client,
                    signatureTimestamp = signatureTimestamp
                ).getOrNull() ?: continue

                if (response.playabilityStatus.status == "UNPLAYABLE" && 
                    response.playabilityStatus.reason?.contains("age-restricted", ignoreCase = true) == true) {
                    throw AgeRestrictedException(response.playabilityStatus.reason ?: "Age restricted content")
                }
                
                if (response.playabilityStatus.status != "OK") continue

                val processed = try {
                    YouTube.newPipePlayer(videoId, response) ?: response
                } catch (e: Exception) {
                    if (e.toString().contains("AgeRestrictedContentException", ignoreCase = true)) {
                        throw AgeRestrictedException("Age restricted content: cannot be watched anonymously")
                    }
                    response
                }

                extractBestAudioStream(processed)?.let { stream ->
                    return stream
                }
            } catch (e: AgeRestrictedException) {
                throw e
            } catch (_: Exception) { }
        }

        return null
    }

    /**
     * Picks the best audio-only format from a [PlayerResponse] and returns full metadata.
     */
    private fun extractBestAudioStream(response: PlayerResponse): ResolvedStream? {
        if (response.playabilityStatus.status != "OK") return null

        val allFormats = response.streamingData?.adaptiveFormats ?: emptyList()
        val audioFormats = allFormats.filter { it.isAudio && it.isOriginal && it.url != null }

        if (audioFormats.isEmpty()) return null

        val mp4Formats = audioFormats.filter { it.mimeType.contains("audio/mp4") }.ifEmpty { audioFormats }

        val bestFormat = when (quality) {
            StreamQuality.LOW -> mp4Formats.sortedBy { it.bitrate }.firstOrNull()
            StreamQuality.NORMAL -> {
                // Pick a mid-range bitrate (prefer ~128kbps)
                val sorted = mp4Formats.sortedBy { it.bitrate }
                sorted.getOrNull(sorted.size / 2) ?: sorted.firstOrNull()
            }
            StreamQuality.HIGH -> mp4Formats.sortedByDescending { it.bitrate }.firstOrNull()
        } ?: return null

        // Extract codecs from mimeType (e.g. "audio/mp4; codecs=\"mp4a.40.2\"" → "mp4a.40.2")
        val codecs = Regex("""codecs="([^"]+)"""").find(bestFormat.mimeType)?.groupValues?.get(1)
            ?: bestFormat.mimeType.substringAfter("codecs=").trim('"')

        return ResolvedStream(
            url = bestFormat.url!!,
            itag = bestFormat.itag,
            mimeType = bestFormat.mimeType,
            bitrate = bestFormat.bitrate,
            contentLength = bestFormat.contentLength,
            sampleRate = bestFormat.audioSampleRate,
            loudnessDb = bestFormat.loudnessDb,
            codecs = codecs,
            expiresInSeconds = response.streamingData?.expiresInSeconds?.toLong()
        )
    }
}
