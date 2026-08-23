package example.nucleus.player

import example.nucleus.data.repository.AudioQuality
import example.nucleus.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * Custom exception for age-restricted content.
 */
class AgeRestrictedException(message: String) : Exception(message)

class AudioStreamResolver(
    private val userPreferences: UserPreferencesRepository,
) {
    /**
     * Resolves audio stream data for a video using the user's current audio quality preference.
     * Si el modo video está habilitado en preferencias, también intenta resolver un stream de
     * video (best-effort; si no se consigue, la reproducción continúa solo con audio).
     *
     * @param videoId The ID of the video to resolve the audio stream for.
     * @return The playback data for the video.
     * @throws Exception If the audio stream cannot be resolved.
     */
    suspend fun resolveAudioStream(videoId: String): PlaybackData {
        val quality = userPreferences.audioQuality.first()
        val videoQuality = if (userPreferences.videoEnabled.first()) {
            userPreferences.videoQuality.first()
        } else {
            null
        }
        return YTPlayerutils.playerResponseForPlayback(
            videoId = videoId,
            audioQuality = quality,
            videoQuality = videoQuality,
        ).fold(
            onSuccess = { data -> data },
            onFailure = { error -> throw Exception("Vaya error: $error") }
        )
    }

    /**
 * Retrieves the current user-selected audio quality preference.
 *
 * @return The user's current audio quality preference.
 */
    suspend fun currentAudioQuality(): AudioQuality = userPreferences.audioQuality.first()
}
