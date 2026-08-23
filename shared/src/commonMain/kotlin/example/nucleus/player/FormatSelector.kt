package example.nucleus.player

import example.nucleus.data.repository.AudioQuality
import com.metrolist.innertube.models.response.PlayerResponse

object FormatSelector {
    fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
    ): PlayerResponse.StreamingData.Format? {
        val formats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?: return null

        return when (audioQuality) {
            AudioQuality.LOW -> formats.minByOrNull { it.bitrate }
            AudioQuality.NORMAL -> {
                val sorted = formats.sortedBy { it.bitrate }
                sorted.getOrNull(sorted.size / 2) ?: sorted.firstOrNull()
            }
            AudioQuality.HIGH -> formats.maxByOrNull { it.bitrate }
        }
    }

    /**
     * Selecciona el mejor formato de video (solo-video, `width != null`) acotado a
     * [maxHeight] píxeles. Prefiere mayor altura y, a igual altura, mayor bitrate.
     * Devuelve null si la respuesta no trae formatos de video elegibles.
     */
    fun findVideoFormat(
        playerResponse: PlayerResponse,
        maxHeight: Int?,
    ): PlayerResponse.StreamingData.Format? {
        val formats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { !it.isAudio && it.isOriginal }
            ?.filter { maxHeight == null || (it.height ?: 0) <= maxHeight }
            ?.filter { (it.height ?: 0) > 0 }
            ?: return null

        return formats.maxWithOrNull(
            compareBy<PlayerResponse.StreamingData.Format> { it.height ?: 0 }
                .thenBy { it.bitrate }
        )
    }

    /**
     * Selecciona el mejor formato **muxed** (video+audio juntos, `formats` progresivo)
     * acotado a [maxHeight]. Prioriza video+audio en un solo archivo para que mpv reproduzca
     * un único flujo (seek robusto, sin pista externa `audio-add`).
     * Devuelve null si no hay progresivo elegible (p. ej. solo DASH separado disponible).
     */
    fun findMuxedFormat(
        playerResponse: PlayerResponse,
        maxHeight: Int?,
    ): PlayerResponse.StreamingData.Format? {
        val progressive = playerResponse.streamingData?.formats
            ?.filter { it.isOriginal }
            ?.filter { maxHeight == null || (it.height ?: 0) <= maxHeight }
            ?.filter { (it.height ?: 0) > 0 }
            // progresivo muxed siempre trae audio (sampleRate / channels no nulos)
            ?.filter { it.audioSampleRate != null || it.audioChannels != null }
            ?: return null
        if (progressive.isEmpty()) return null
        return progressive.maxWithOrNull(
            compareBy<PlayerResponse.StreamingData.Format> { it.height ?: 0 }
                .thenBy { it.bitrate }
        )
    }
}
