package example.nucleus.player

import com.metrolist.innertube.models.response.PlayerResponse

data class PlaybackData(
    val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
    val videoDetails: PlayerResponse.VideoDetails?,
    val playbackTracking: PlayerResponse.PlaybackTracking?,
    val format: PlayerResponse.StreamingData.Format,
    val streamUrl: String,
    val streamExpiresInSeconds: Int,
    val videoFormat: PlayerResponse.StreamingData.Format? = null,
    val videoUrl: String? = null,
)
