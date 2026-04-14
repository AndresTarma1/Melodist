package com.example.melodist.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.YTItem

/**
 * Returns true if the thumbnail URL corresponds to a wide (16:9) image,
 * as opposed to a square album/artist cover.
 */
fun YTItem.isWideThumbnail(): Boolean {
    if (this is ArtistItem || this is AlbumItem) return false
    return thumbnail?.let { url ->
        url.contains("ytimg.com/vi/") ||
        url.contains("hqdefault") ||
        url.contains("mqdefault") ||
        url.contains("maxresdefault") ||
        url.contains("sddefault")
    } == true
}

/**
 * Returns the correct aspect ratio for a YTItem thumbnail.
 * - Artists and albums → 1:1
 * - Songs / playlists with video thumbnails → 16:9
 */
fun YTItem.thumbnailAspectRatio(): Float = if (isWideThumbnail()) 16f / 9f else 1f

/**
 * Returns the recommended card width for a MusicItem card.
 * Wide (16:9) items get a wider card to preserve readability.
 */
fun YTItem.musicItemCardWidth(): Dp = if (isWideThumbnail()) 280.dp else 200.dp


