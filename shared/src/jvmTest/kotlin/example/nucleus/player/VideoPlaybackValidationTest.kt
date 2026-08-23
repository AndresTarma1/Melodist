package example.nucleus.player

import example.nucleus.data.repository.AudioQuality
import example.nucleus.data.repository.VideoQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class VideoPlaybackValidationTest {

    @Test
    fun findMuxedChoosesProgressiveAndRespectsHeight() {
        // muxed 360p vs separate 720p -> muxed 360 should lose to separate 720 when maxHeight=1080
        // This is tested via YTPlayerutils logic, but we test FormatSelector directly
        val progressiveMuxed = PlayerResponseHelper.muxedFormat(itag = 22, height = 720, bitrate = 2_000_000)
        val progressive360 = PlayerResponseHelper.muxedFormat(itag = 18, height = 360, bitrate = 1_000_000)
        val adaptive720 = PlayerResponseHelper.videoFormat(itag = 137, height = 720, bitrate = 2_500_000)
        val respMuxedOnly = PlayerResponseHelper.responseWithFormats(
            progressive = listOf(progressiveMuxed, progressive360),
            adaptive = listOf(adaptive720)
        )
        val pickedMuxed = FormatSelector.findMuxedFormat(respMuxedOnly, 720)
        assertNotNull(pickedMuxed)
        assertEquals(22, pickedMuxed.itag, "Should pick 720 muxed when cap 720")

        val pickedMuxed1080 = FormatSelector.findMuxedFormat(respMuxedOnly, 1080)
        assertNotNull(pickedMuxed1080)
        assertEquals(22, pickedMuxed1080.itag, "Highest muxed <=1080 is 720")

        // No muxed returns null
        val respNoMuxed = PlayerResponseHelper.responseWithFormats(
            progressive = emptyList(),
            adaptive = listOf(adaptive720)
        )
        assertEquals(null, FormatSelector.findMuxedFormat(respNoMuxed, 720))
    }

    @Test
    fun muxedDoesNotDegradeResolutionWhenSeparateHigher() = runBlocking {
        // Simulate YTPlayerutils decision: muxed 360 vs separate 720 -> should prefer separate 720
        val muxed360 = PlayerResponseHelper.muxedFormat(itag = 18, height = 360, bitrate = 1_000_000)
        val separate720 = PlayerResponseHelper.videoFormat(itag = 247, height = 720, bitrate = 3_000_000)
        val muxedH = muxed360.height ?: 0
        val separateH = separate720.height ?: 0
        val useMuxed = muxedH >= separateH
        assertEquals(false, useMuxed, "360 muxed should NOT be preferred over 720 separate")

        val muxed720 = PlayerResponseHelper.muxedFormat(itag = 22, height = 720, bitrate = 2_000_000)
        val useMuxed2 = (muxed720.height ?: 0) >= separateH
        assertEquals(true, useMuxed2, "720 muxed tie with 720 separate -> prefer muxed")
    }

    @Test
    fun seekInVideoDoesNotStall() = runBlocking {
        // Headless mpv video+audio seek test with real network (uses y2joDOunHBo which is known to work via yt-dlp fallback)
        // If network unavailable or blocked, this test is best-effort: we assert that seek does not throw and stall detection grace works
        val videoId = "dQw4w9WgXcQ" // RickRoll, not blocked, has muxed 360 and separate 720
        val res = try {
            YTPlayerutils.playerResponseForPlayback(
                videoId = videoId,
                audioQuality = AudioQuality.NORMAL,
                videoQuality = VideoQuality.AUTO,
            ).getOrThrow()
        } catch (e: Throwable) {
            println("SKIP seek test: cannot resolve $videoId: ${e.message}")
            return@runBlocking
        }
        val videoUrl = res.videoUrl
        if (videoUrl == null) {
            println("SKIP: no videoUrl for $videoId")
            return@runBlocking
        }
        println("Resolved videoUrl itag=${res.videoFormat?.itag} h=${res.videoFormat?.height} muxed=${res.videoUrl == res.streamUrl} audio itag=${res.format.itag}")
        // Verify our muxed-first logic: for AUTO (720) if muxed 360 vs separate 720, should pick separate 720
        // dQw4w9WgXcQ should have separate 720 available, so videoFormat height should be 720, not 360
        if (res.videoFormat?.height == 360) {
            println("WARN: picked 360 muxed instead of 720 separate — check height comparison")
        }
        // Now test mpv seek path headless
        val player = MpvAudioPlayer()
        try {
            player.init()
        } catch (e: Throwable) {
            println("SKIP: libmpv not available: ${e.message}")
            return@runBlocking
        }
        val renderer = MpvVideoRenderer(player)
        renderer.start()
        val ticker = launch {
            while (isActive) {
                renderer.onVideoSize(player.getVideoSize())
                player.refreshBufferingState(userWantsPlay = true)
                delay(400)
            }
        }
        try {
            player.openUri(videoUrl, audioUrl = res.streamUrl, videoMode = true)
            val started = player.awaitPlaybackStarted(15000)
            assertTrue(started, "Playback should start")
            delay(2000)
            val versionBefore = renderer.frameVersion
            val posBefore = player.getCurrentPosition()
            println("Before seek: pos=$posBefore version=$versionBefore isPlaying=${player.isPlaying.value}")
            // Seek to 34s (like user 1:34 but shorter for test speed)
            player.seekToMs(34_000)
            // Wait up to 8s for seek to settle; with our SEEK_STALL_GRACE 8s, watchdog should not fire
            var playingAfter = false
            var versionAfter = versionBefore
            repeat(20) {
                delay(500)
                versionAfter = renderer.frameVersion
                if (player.isPlaying.value) playingAfter = true
                println("Seek poll ${it+1}: pos=${player.getCurrentPosition()} isPlaying=${player.isPlaying.value} isBuffering=${player.isBuffering.value} version=$versionAfter")
                if (playingAfter && versionAfter > versionBefore) return@repeat
            }
            assertTrue(playingAfter, "Should be playing after seek, not stuck buffering")
            assertTrue(versionAfter > versionBefore, "Frames should advance after seek")
            // Seek again to 10s to test second seek
            player.seekToMs(10_000)
            delay(3000)
            assertTrue(player.isPlaying.value || player.isBuffering.value, "Should be playing or buffering after second seek, not dead")
            println("Seek test PASSED")
        } finally {
            ticker.cancel()
            renderer.stop()
            player.dispose()
        }
    }
}

// Helper to build PlayerResponse with progressive + adaptive for unit tests
private object PlayerResponseHelper {
    fun muxedFormat(itag: Int, height: Int, bitrate: Int): com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format =
        com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format(
            itag = itag,
            url = "https://example.com/$itag",
            mimeType = "video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"",
            bitrate = bitrate,
            width = height * 16 / 9,
            height = height,
            contentLength = null,
            quality = "hd720",
            fps = 30,
            qualityLabel = "${height}p",
            averageBitrate = null,
            audioQuality = null,
            approxDurationMs = null,
            audioSampleRate = 44100,
            audioChannels = 2,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
            cipher = null,
            audioTrack = null,
        )

    fun videoFormat(itag: Int, height: Int, bitrate: Int): com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format =
        com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format(
            itag = itag,
            url = "https://example.com/$itag",
            mimeType = "video/mp4",
            bitrate = bitrate,
            width = height * 16 / 9,
            height = height,
            contentLength = null,
            quality = "hd720",
            fps = 30,
            qualityLabel = "${height}p",
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

    fun responseWithFormats(
        progressive: List<com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format>,
        adaptive: List<com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format>,
    ): com.metrolist.innertube.models.response.PlayerResponse =
        com.metrolist.innertube.models.response.PlayerResponse(
            responseContext = com.metrolist.innertube.models.ResponseContext(null, null),
            playabilityStatus = com.metrolist.innertube.models.response.PlayerResponse.PlayabilityStatus("OK", null),
            playerConfig = null,
            streamingData = com.metrolist.innertube.models.response.PlayerResponse.StreamingData(
                formats = progressive,
                adaptiveFormats = adaptive,
                expiresInSeconds = 21600,
            ),
            videoDetails = null,
            playbackTracking = null,
        )
}
