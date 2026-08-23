package example.nucleus.player

import com.metrolist.innertube.models.response.PlayerResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class FormatSelectorTest {

    private fun videoFormat(itag: Int, height: Int?, bitrate: Int, autoDubbed: Boolean? = null) =
        PlayerResponse.StreamingData.Format(
            itag = itag,
            url = null,
            mimeType = "video/mp4",
            bitrate = bitrate,
            width = height?.let { it * 16 / 9 },
            height = height,
            contentLength = null,
            quality = "q",
            fps = 30,
            qualityLabel = height?.let { "${it}p" },
            averageBitrate = null,
            audioQuality = null,
            approxDurationMs = null,
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
            cipher = null,
            audioTrack = autoDubbed?.let {
                PlayerResponse.StreamingData.Format.AudioTrack(
                    displayName = null,
                    id = null,
                    isAutoDubbed = it,
                )
            },
        )

    private fun audioFormat(itag: Int, bitrate: Int) =
        PlayerResponse.StreamingData.Format(
            itag = itag,
            url = null,
            mimeType = "audio/webm",
            bitrate = bitrate,
            width = null,
            height = null,
            contentLength = null,
            quality = "q",
            fps = null,
            qualityLabel = null,
            averageBitrate = null,
            audioQuality = null,
            approxDurationMs = null,
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
            cipher = null,
            audioTrack = null,
        )

    private fun playerResponse(formats: List<PlayerResponse.StreamingData.Format>) =
        PlayerResponse(
            responseContext = com.metrolist.innertube.models.ResponseContext(
                visitorData = null,
                serviceTrackingParams = null,
            ),
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK", reason = null),
            playerConfig = null,
            streamingData = PlayerResponse.StreamingData(
                formats = null,
                adaptiveFormats = formats,
                expiresInSeconds = 21600,
            ),
            videoDetails = null,
            playbackTracking = null,
        )

    @Test
    fun `findVideoFormat picks highest eligible height under cap`() {
        val response = playerResponse(
            listOf(
                videoFormat(137, 1080, 4_000_000),
                videoFormat(136, 720, 2_000_000),
                videoFormat(135, 480, 1_000_000),
                audioFormat(140, 128_000),
            )
        )
        val picked = FormatSelector.findVideoFormat(response, 720)
        assertNotNull(picked)
        assertEquals(136, picked.itag)
    }

    @Test
    fun `findVideoFormat skips audio formats and auto-dubbed tracks`() {
        val response = playerResponse(
            listOf(
                audioFormat(140, 128_000),
                videoFormat(137, 1080, 4_000_000, autoDubbed = true),
            )
        )
        assertNull(FormatSelector.findVideoFormat(response, 1080))
    }

    @Test
    fun `findVideoFormat returns null when no video formats exist`() {
        val response = playerResponse(listOf(audioFormat(140, 128_000)))
        assertNull(FormatSelector.findVideoFormat(response, null))
    }

    @Test
    fun `findVideoFormat returns null when all formats exceed the cap`() {
        val response = playerResponse(listOf(videoFormat(137, 1080, 4_000_000)))
        assertNull(FormatSelector.findVideoFormat(response, 480))
    }
}
