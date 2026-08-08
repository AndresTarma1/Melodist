package com.metrolist.innertube.pages

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.MusicResponsiveListItemRenderer
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.oddElements
import com.metrolist.innertube.models.splitBySeparator
import com.metrolist.innertube.models.splitArtistRuns
import com.metrolist.innertube.utils.parseTime

data class SearchResult(
    val items: List<YTItem>,
    val continuation: String? = null,
)

object SearchPage {
    fun toYTItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
        val secondaryLine =
            renderer.flexColumns
                .getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.splitBySeparator()
                ?: return null
        return when {
            // CRITICAL: Check isEpisode BEFORE isSong — both can match isSong (watchEndpoint or
            // null navigationEndpoint), so episodes must be identified first.
            renderer.isEpisode -> {
                val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

                // The subtitle line structure differs between filtered and unfiltered search:
                //   Unfiltered: ["Episode", "·", "Jan 2025", "·", "Podcast Name", "·", "1:00:00"]
                //     → secondaryLine = [["Episode"], ["Jan 2025"], ["Podcast Name"], ["1:00:00"]]
                //   Filtered:   ["Jan 2025", "·", "Podcast Name"]
                //     → secondaryLine = [["Jan 2025"], ["Podcast Name"]]
                //
                // Strategy: locate the podcast section by its PODCAST_SHOW_DETAIL_PAGE link;
                // the date is in the section immediately before it.
                val podcastSectionIndex = secondaryLine.indexOfFirst { section ->
                    section.any { run ->
                        run.navigationEndpoint?.browseEndpoint
                            ?.browseEndpointContextSupportedConfigs
                            ?.browseEndpointContextMusicConfig
                            ?.pageType == MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE
                    }
                }

                val podcast = if (podcastSectionIndex >= 0) {
                    secondaryLine[podcastSectionIndex].firstOrNull()?.let { run ->
                        Album(
                            name = run.text,
                            id = run.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                        )
                    }
                } else null

                val publishDateText = if (podcastSectionIndex > 0)
                    secondaryLine.getOrNull(podcastSectionIndex - 1)?.firstOrNull()?.text
                else null

                EpisodeItem(
                    id = renderer.playlistItemData?.videoId
                        ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
                        ?: renderer.overlay?.musicItemThumbnailOverlayRenderer
                            ?.content?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint?.watchEndpoint?.videoId
                        ?: renderer.flexColumns.firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text?.runs?.firstOrNull()
                            ?.navigationEndpoint?.watchEndpoint?.videoId
                        ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    author = null,
                    podcast = podcast,
                    duration =
                        secondaryLine
                            .lastOrNull()
                            ?.firstOrNull()
                            ?.text
                            ?.parseTime(),
                    publishDateText = publishDateText,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    explicit =
                        renderer.badges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null,
                    // In filtered search the overlay play button may be absent; fall back to the
                    // item's own watchEndpoint so the episode is always playable.
                    endpoint = renderer.overlay
                        ?.musicItemThumbnailOverlayRenderer
                        ?.content
                        ?.musicPlayButtonRenderer
                        ?.playNavigationEndpoint
                        ?.watchEndpoint
                        ?: renderer.navigationEndpoint?.watchEndpoint,
                    libraryAddToken = libraryTokens.addToken,
                    libraryRemoveToken = libraryTokens.removeToken,
                )
            }
            renderer.isSong -> {
                val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

                // Search bylines are not consistent for uploaded songs. A regular YTM song is
                // usually `artist • album • duration`, while an upload can be
                // `date • uploader • duration`. Do not use fixed positions or the date becomes
                // an artist and the uploader becomes an album.
                val metadataGroups = secondaryLine.filterNot { group ->
                    group.firstOrNull()?.text?.let(::isDateMetadata) == true ||
                        group.firstOrNull()?.text?.parseTime() != null
                }
                val artistGroupIndex = secondaryLine.indexOfFirst { group ->
                    group.any { it.navigationEndpoint?.browseEndpoint?.isArtistEndpoint == true }
                }.takeIf { it >= 0 } ?: secondaryLine.indexOfFirst { group ->
                    group in metadataGroups
                }
                val artistGroup = secondaryLine.getOrNull(artistGroupIndex).orEmpty()
                val artistRuns = artistGroup
                    .filter { it.navigationEndpoint?.browseEndpoint?.isArtistEndpoint == true }
                    .ifEmpty { artistGroup }
                    .splitArtistRuns()
                val albumGroup = secondaryLine.drop(artistGroupIndex + 1)
                    .firstOrNull { group ->
                        group.any { it.navigationEndpoint?.browseEndpoint?.isAlbumEndpoint == true }
                    }
                val artists = artistRuns.map { run ->
                    Artist(
                        name = run.text,
                        id = run.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                }

                SongItem(
                    id = renderer.playlistItemData?.videoId
                        ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
                        ?: renderer.overlay?.musicItemThumbnailOverlayRenderer
                            ?.content?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint?.watchEndpoint?.videoId
                        ?: renderer.flexColumns.firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text?.runs?.firstOrNull()
                            ?.navigationEndpoint?.watchEndpoint?.videoId
                        ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    artists = artists.ifEmpty { return null },
                    album =
                        albumGroup?.firstOrNull()?.takeIf { it.navigationEndpoint?.browseEndpoint?.isAlbumEndpoint == true }?.let {
                            Album(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
                            )
                        },
                    duration =
                        secondaryLine
                            .lastOrNull()
                            ?.firstOrNull()
                            ?.text
                            ?.parseTime(),
                    musicVideoType = renderer.musicVideoType,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    explicit =
                        renderer.badges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null,
                    libraryAddToken = libraryTokens.addToken,
                    libraryRemoveToken = libraryTokens.removeToken,
                    isEpisode = renderer.isEpisode
                )
            }
            renderer.isArtist -> {
                ArtistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                            ?: return null,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    shuffleEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint ?: return null,
                    radioEndpoint =
                        renderer.menu.menuRenderer.items
                            .find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint ?: return null,
                )
            }
            renderer.isUserChannel -> {
                ArtistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                            ?: return null,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    shuffleEndpoint = renderer.menu
                        ?.menuRenderer
                        ?.items
                        ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                        ?.menuNavigationItemRenderer
                        ?.navigationEndpoint
                        ?.watchPlaylistEndpoint,
                    radioEndpoint = renderer.menu
                        ?.menuRenderer
                        ?.items
                        ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                        ?.menuNavigationItemRenderer
                        ?.navigationEndpoint
                        ?.watchPlaylistEndpoint,
                    isProfile = true,
                )
            }
            renderer.isAlbum -> {
                AlbumItem(
                    browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    playlistId =
                        renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.anyWatchEndpoint
                            ?.playlistId
                            ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    artists =
                        secondaryLine.getOrNull(1)?.oddElements()?.map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId,
                            )
                        } ?: return null,
                    year =
                        secondaryLine
                            .getOrNull(2)
                            ?.firstOrNull()
                            ?.text
                            ?.toIntOrNull(),
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    explicit =
                        renderer.badges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null,
                )
            }
            renderer.isPlaylist -> {
                PlaylistItem(
                    id =
                        renderer.navigationEndpoint
                            ?.browseEndpoint
                            ?.browseId
                            ?.removePrefix("VL") ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    author =
                        secondaryLine.firstOrNull()?.firstOrNull()?.let {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId,
                            )
                        } ?: return null,
                    songCountText =
                        renderer.flexColumns
                            .getOrNull(1)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.lastOrNull()
                            ?.text ?: return null,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    playEndpoint =
                        renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint ?: return null,
                    shuffleEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint ?: return null,
                    radioEndpoint =
                        renderer.menu.menuRenderer.items
                            .find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint ?: return null,
                )
            }
            renderer.isPodcast -> {
                PodcastItem(
                    id =
                        renderer.navigationEndpoint
                            ?.browseEndpoint
                            ?.browseId
                            ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    author =
                        secondaryLine.firstOrNull()?.firstOrNull()?.let {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId,
                            )
                        },
                    episodeCountText =
                        renderer.flexColumns
                            .getOrNull(1)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.lastOrNull()
                            ?.text,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    playEndpoint =
                        renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint,
                    shuffleEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                )
            }
            else -> null
        }
    }

    private fun isDateMetadata(text: String): Boolean {
        val value = text.trim()
        return value.matches(Regex("\\d{1,2}\\s+[\\p{L}.]+\\s+\\d{4}")) ||
            value.matches(Regex("[A-Za-z]+\\s+\\d{1,2},?\\s+\\d{4}")) ||
            value.matches(Regex("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}"))
    }
}
