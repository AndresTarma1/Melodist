package com.example.melodist.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.ArtistScreenSkeleton
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.BlurredImageBackground
import com.example.melodist.ui.components.song.DownloadIndicator
import com.example.melodist.ui.components.layout.HorizontalScrollableRow
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.context.SongContextMenu
import com.example.melodist.ui.helpers.contextMenuArea
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.ArtistState
import com.example.melodist.viewmodels.ArtistViewModel
import com.metrolist.innertube.models.*
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.ArtistSection

@Composable
fun ArtistScreenRoute(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistViewModel,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val uiState by viewModel.uiState.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val successState = uiState as? ArtistState.Success

    val actions = remember(viewModel, playerViewModel, successState) {
        ArtistScreenActions(
            onToggleSave = { viewModel.toggleSave() },
            onPlayTopSongs = {
                val artistPage = successState?.artistPage ?: return@ArtistScreenActions
                val songs = artistPage.sections.flatMap { it.items }.filterIsInstance<SongItem>()
                if (songs.isNotEmpty()) playerViewModel.playCustom(songs, 0)
            },
            onShuffleTopSongs = {
                val artistPage = successState?.artistPage ?: return@ArtistScreenActions
                val songs = artistPage.sections.flatMap { it.items }.filterIsInstance<SongItem>()
                if (songs.isNotEmpty()) playerViewModel.playCustom(songs.shuffled(), 0)
            }
        )
    }

    ArtistScreen(uiState, onNavigate, onBack, isSaved = isSaved, actions = actions)
}

data class ArtistScreenActions(
    val onToggleSave: () -> Unit,
    val onPlayTopSongs: () -> Unit,
    val onShuffleTopSongs: () -> Unit,
)

@Composable
fun ArtistScreen(
    uiState: ArtistState,
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    isSaved: Boolean = false,
    actions: ArtistScreenActions,
) {
    val thumbnailUrl = (uiState as? ArtistState.Success)?.artistPage?.artist?.thumbnail

    BlurredImageBackground(
        imageUrl = thumbnailUrl,
        modifier = Modifier.fillMaxSize(),
        darkOverlayAlpha = 0.55f,
        gradientFraction = 0.40f
    ) {
        when (uiState) {
            is ArtistState.Loading -> ArtistScreenSkeleton()
            is ArtistState.Success -> ArtistScreenLayout(
                uiState.artistPage,
                onNavigate,
                isSaved = isSaved,
                actions = actions
            )

            is ArtistState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.message, color = MaterialTheme.colorScheme.error)
            }
        }

        // Floating back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Atrás",
                tint = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun ArtistScreenLayout(
    artistPage: ArtistPage,
    onNavigate: (Route) -> Unit,
    isSaved: Boolean = false,
    actions: ArtistScreenActions,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ArtistWideLayout(
            artistPage = artistPage,
            onNavigate = onNavigate,
            isSaved = isSaved,
            actions = actions
        )
    }
}

@Composable
private fun ArtistWideLayout(
    artistPage: ArtistPage,
    onNavigate: (Route) -> Unit,
    isSaved: Boolean,
    actions: ArtistScreenActions
) {
    val scrollState = rememberScrollState()
    val hasPlayableSongs = remember(artistPage.sections) {
        artistPage.sections.any { section -> section.items.any { it is SongItem } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 48.dp, vertical = 24.dp)
                .padding(top = 32.dp)
        ) {
            // Header: avatar + info side by side
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Circular avatar with translucent background
                Box(
                    modifier = Modifier.size(196.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Translucent background circle behind the avatar
                    Box(
                        modifier = Modifier
                            .size(196.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
                            )
                    )
                    Card(
                        modifier = Modifier
                            .size(180.dp)
                            .shadow(24.dp, CircleShape),
                        shape = CircleShape
                    ) {
                        MelodistImage(
                            url = artistPage.artist.thumbnail,
                            contentDescription = artistPage.artist.title,
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            placeholderType = PlaceholderType.ARTIST,
                            iconSize = 72.dp,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter
                        )
                    }
                }

                Spacer(Modifier.width(32.dp))

                Column {
                    Text(
                        text = artistPage.artist.title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(6.dp))

                    artistPage.subscriberCountText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    artistPage.monthlyListenerCount?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Action buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = actions.onToggleSave,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                if (isSaved) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                                null,
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        FloatingActionButton(
                            onClick = { if (hasPlayableSongs) actions.onPlayTopSongs() },
                            shape = CircleShape,
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            modifier = Modifier
                                .size(56.dp)
                                .pointerHoverIcon(if (hasPlayableSongs) PointerIcon.Hand else PointerIcon.Default)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(28.dp))
                        }

                        IconButton(
                            onClick = { if (hasPlayableSongs) actions.onShuffleTopSongs() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .pointerHoverIcon(if (hasPlayableSongs) PointerIcon.Hand else PointerIcon.Default)
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // Sections
            artistPage.sections.forEach { section ->
                ArtistSectionRow(
                    section,
                    onNavigate,
                )
                Spacer(Modifier.height(24.dp))
            }

            // Description
            artistPage.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Acerca de",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        AppVerticalScrollbar(
            state = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(12.dp)
        )
    }
}

// ── Section row (horizontal carousel) ──
@Composable
private fun ArtistSectionRow(
    section: ArtistSection,
    onNavigate: (Route) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(section.items) { item ->
                ArtistSectionCard(
                    item,
                    onNavigate
                )
            }

        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ArtistSectionCard(
    item: YTItem,
    onNavigate: (Route) -> Unit
) {
    val isArtist = item is ArtistItem
    val cardShape = if (isArtist) CircleShape else RoundedCornerShape(8.dp)

    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current

    var isHovered by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }


    val downloadState by if (item is SongItem) {
        rememberSongDownloadState(item.id, downloadViewModel)
    } else {
        remember { mutableStateOf(null) }
    }

    Box {
        Column(
            modifier = Modifier
                .width(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if(isHovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                .clickable {
                    when (item) {
                        is AlbumItem -> onNavigate(Route.Album(item.browseId))
                        is PlaylistItem -> onNavigate(Route.Playlist(item.id))
                        is ArtistItem -> onNavigate(Route.Artist(item.id))
                        is SongItem -> playerViewModel.playSingle(item)
                    }
                }
                .pointerHoverIcon(PointerIcon.Hand)
                .contextMenuArea(
                    enabled = item is SongItem,
                    onHoverChange = { isHovered = it },
                    onMenuAction = { offset ->
                        menuOffset = offset
                        showMenu = true
                    }
                )
                .padding(8.dp),
            horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start
        ) {
            // Thumbnail
            Box {
                MelodistImage(
                    url = item.thumbnail,
                    contentDescription = item.title,
                    modifier = Modifier.size(134.dp),
                    shape = cardShape,
                    placeholderType = when (item) {
                        is ArtistItem -> PlaceholderType.ARTIST
                        is AlbumItem -> PlaceholderType.ALBUM
                        is PlaylistItem -> PlaceholderType.PLAYLIST
                        else -> PlaceholderType.SONG
                    },
                    contentScale = ContentScale.Crop,
                    iconSize = 40.dp
                )

                if (item is SongItem && downloadState != null) {
                    DownloadIndicator(
                        state = downloadState,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape)
                            .padding(4.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            // Subtitle
            val subtitle = when (item) {
                is SongItem -> item.artists.joinToString(", ") { it.name }
                is AlbumItem -> item.year?.toString() ?: "Álbum"
                is ArtistItem -> "Artista"
                is PlaylistItem -> item.author?.name ?: "Playlist"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (item is SongItem) {
            SongContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                song = item,
                offset = menuOffset
            )
        }
    }
}



