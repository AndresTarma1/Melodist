package com.example.melodist.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.ArtistScreenSkeleton
import com.example.melodist.ui.components.BlurredImageBackground
import com.example.melodist.ui.components.HorizontalScrollableRow
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.ArtistState
import com.example.melodist.viewmodels.ArtistViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.*
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.ArtistSection
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ArtistScreenRoute(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistViewModel,
) {

    val playerViewModel = LocalPlayerViewModel.current

    val uiState by viewModel.uiState.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()

    ArtistScreen(uiState, onNavigate, onBack, isSaved = isSaved, onToggleSave = { viewModel.toggleSave() })
}

@Composable
fun ArtistScreen(
    uiState: ArtistState,
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {}
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
            is ArtistState.Success -> ArtistScreenContent(uiState.artistPage, onNavigate, isSaved = isSaved, onToggleSave = onToggleSave)
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
fun ArtistScreenContent(artistPage: ArtistPage, onNavigate: (Route) -> Unit, isSaved: Boolean = false, onToggleSave: () -> Unit = {}) {
    val surfaceColor = Color.Transparent
    val onSurfaceColor = Color.White
    val onSurfaceVariant = Color.White.copy(alpha = 0.65f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 800.dp


        if (isCompact) {
            ArtistCompact(artistPage, onSurfaceColor, onSurfaceVariant, surfaceColor, onNavigate, isSaved, onToggleSave)
        } else {
            ArtistWide(artistPage, onSurfaceColor, onSurfaceVariant, surfaceColor, onNavigate, isSaved, onToggleSave)
        }
    }
}

// ── Wide layout ──
@Composable
private fun ArtistWide(
    artistPage: ArtistPage,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    surfaceColor: Color,
    onNavigate: (Route) -> Unit,
    isSaved: Boolean,
    onToggleSave: () -> Unit
) {
    val scrollState = rememberScrollState()

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
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
                        color = onSurfaceColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(6.dp))

                    artistPage.subscriberCountText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurfaceVariant
                        )
                    }

                    artistPage.monthlyListenerCount?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Action buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = onToggleSave,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                if (isSaved) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                                null,
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else onSurfaceColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        FloatingActionButton(
                            onClick = { /* TODO */ },
                            shape = CircleShape,
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            modifier = Modifier.size(56.dp).pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(28.dp))
                        }

                        IconButton(
                            onClick = { /* TODO */ },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.Default.Shuffle, null, tint = onSurfaceColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // Sections
            artistPage.sections.forEach { section ->
                ArtistSectionRow(section, onNavigate, onSurfaceColor, onSurfaceVariant)
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
                        color = onSurfaceColor
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp, horizontal = 4.dp),
            style = LocalScrollbarStyle.current.copy(
                thickness = 4.dp,
                unhoverColor = onSurfaceVariant.copy(alpha = 0.08f),
                hoverColor = onSurfaceVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(2.dp)
            )
        )
    }
}

// ── Compact layout ──
@Composable
private fun ArtistCompact(
    artistPage: ArtistPage,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    surfaceColor: Color,
    onNavigate: (Route) -> Unit,
    isSaved: Boolean,
    onToggleSave: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar with translucent background
            Box(
                modifier = Modifier.size(176.dp),
                contentAlignment = Alignment.Center
            ) {
                // Translucent background circle
                Box(
                    modifier = Modifier
                        .size(176.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
                        )
                )
                Card(
                    modifier = Modifier
                        .size(160.dp)
                        .shadow(16.dp, CircleShape),
                    shape = CircleShape
                ) {
                    MelodistImage(
                        url = artistPage.artist.thumbnail,
                        contentDescription = artistPage.artist.title,
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        placeholderType = PlaceholderType.ARTIST,
                        iconSize = 64.dp,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        alignment = Alignment.TopCenter
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = artistPage.artist.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = onSurfaceColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            artistPage.subscriberCountText?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = onSurfaceVariant)
            }

            artistPage.monthlyListenerCount?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = onSurfaceVariant.copy(alpha = 0.7f))
            }

            Spacer(Modifier.height(20.dp))

            // Action buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        if (isSaved) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                        null,
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else onSurfaceColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                FloatingActionButton(
                    onClick = { /* TODO */ },
                    shape = CircleShape,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.size(56.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(28.dp))
                }

                IconButton(
                    onClick = { /* TODO */ },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(Icons.Default.Shuffle, null, tint = onSurfaceColor, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            // Sections
            artistPage.sections.forEach { section ->
                ArtistSectionRow(section, onNavigate, onSurfaceColor, onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
            }

            // Description
            artistPage.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Acerca de",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp),
            style = LocalScrollbarStyle.current.copy(
                thickness = 4.dp,
                unhoverColor = onSurfaceVariant.copy(alpha = 0.08f),
                hoverColor = onSurfaceVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(2.dp)
            )
        )
    }
}

// ── Section row (horizontal carousel) ──
@Composable
private fun ArtistSectionRow(
    section: ArtistSection,
    onNavigate: (Route) -> Unit,
    onSurfaceColor: Color,
    onSurfaceVariant: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = onSurfaceColor,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
             state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ){
            items(section.items) { item ->
                ArtistSectionCard(item, onNavigate, onSurfaceColor, onSurfaceVariant)
            }

        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ArtistSectionCard(
    item: YTItem,
    onNavigate: (Route) -> Unit,
    onSurfaceColor: Color,
    onSurfaceVariant: Color
) {
    val isArtist = item is ArtistItem
    val cardShape = if (isArtist) CircleShape else RoundedCornerShape(8.dp)

    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = com.example.melodist.utils.LocalDownloadViewModel.current
    val scope = rememberCoroutineScope()

    var isHovered by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var itemHeight by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    val elevation by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.surfaceContainerHigh
        else Color.Transparent
    )

    val downloadState by if (item is SongItem) {
        com.example.melodist.ui.helpers.rememberSongDownloadState(item.id, downloadViewModel)
    } else {
        remember { mutableStateOf(null) }
    }

    Box(modifier = Modifier.onGloballyPositioned { itemHeight = it.size.height }) {
        Column(
            modifier = Modifier
                .width(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(elevation)
                .clickable {
                    when (item) {
                        is AlbumItem -> onNavigate(Route.Album(item.browseId))
                        is PlaylistItem -> onNavigate(Route.Playlist(item.id))
                        is ArtistItem -> onNavigate(Route.Artist(item.id))
                        is SongItem -> playerViewModel.playSingle(item)
                    }
                }
                .pointerHoverIcon(PointerIcon.Hand)
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                .onPointerEvent(PointerEventType.Press) {
                    if (item is SongItem && it.button == androidx.compose.ui.input.pointer.PointerButton.Secondary) {
                        val position = it.changes.first().position
                        val xDp = with(density) { position.x.toDp() }
                        val yDp = with(density) { (position.y - itemHeight).toDp() }
                        menuOffset = androidx.compose.ui.unit.DpOffset(xDp, yDp)
                        showMenu = true
                    }
                }
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
                    com.example.melodist.ui.components.DownloadIndicator(
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
                color = onSurfaceColor,
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
                color = onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (item is SongItem) {
            com.example.melodist.ui.components.SongContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                song = item,
                downloadState = downloadState,
                onDownload = {
                    scope.launch {
                        val enrichedSong = if (item.duration == null || item.album == null) {
                            com.metrolist.innertube.YouTube.next(com.metrolist.innertube.models.WatchEndpoint(videoId = item.id))
                                .getOrNull()?.items?.firstOrNull { it.id == item.id } ?: item
                        } else item
                        downloadViewModel.downloadSong(enrichedSong)
                    }
                },
                onRemoveDownload = { downloadViewModel.removeDownload(item.id) },
                onCancelDownload = { downloadViewModel.cancelDownload(item.id) },
                onAddToQueue = { playerViewModel.addToQueue(item) },
                onPlayNext = { playerViewModel.playNext(item) },
                offset = menuOffset
            )
        }
    }
}



