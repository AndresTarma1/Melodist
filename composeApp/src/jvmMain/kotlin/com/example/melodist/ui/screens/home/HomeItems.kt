package com.example.melodist.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Horizontal
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.melodist.ui.components.BoxForContainerContextMenuItem
import com.example.melodist.ui.components.HoverCornerActionButton
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.context.SongContextMenu
import com.example.melodist.ui.components.song.DownloadIndicator
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.thumbnailAspectRatio
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.modifier.onHover
import kotlin.collections.isNotEmpty
import kotlin.collections.orEmpty

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SongHomeItem(item: SongItem, onClick: (YTItem) -> Unit) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(item.id, downloadViewModel)

    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

    BaseHomeItem(
        item = item,
        onClick = onClick,
        imageShape = RoundedCornerShape(10.dp),
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.SONG,
        centerPlayVisible = true,
        contextMenuEnabled = true,
        onContextMenuAction = { offset -> menuOffset = offset; showMenu = true },
        subtitle = item.artists.firstOrNull()?.name.orEmpty(),
        topStartOverlay = {
            if (downloadState != null) {
                DownloadIndicator(
                    state = downloadState,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .padding(4.dp)
                )
            }
        },
        overlayContent = {
            SongContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                song = item,
                offset = menuOffset
            )
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AlbumHomeItem(item: AlbumItem, onClick: (YTItem) -> Unit) {
    val playerViewModel = LocalPlayerViewModel.current
    val scope = rememberCoroutineScope()

    BaseHomeItem(
        item = item,
        onClick = onClick,
        imageShape = RoundedCornerShape(10.dp),
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.ALBUM,
        centerPlayVisible = false,
        contextMenuEnabled = false,
        onMoreClick = { onClick(item) },
        quickPlay = QuickPlayConfig(
            size = 28.dp,
            iconSize = 16.dp,
            onClick = {
                scope.launch {
                    val page = YouTube.album(item.browseId).getOrNull()
                    val songs = page?.songs.orEmpty()
                    if (songs.isNotEmpty()) {
                        playerViewModel.playAlbum(
                            songs = songs,
                            startIndex = 0,
                            browseId = item.browseId,
                            title = item.title
                        )
                    } else {
                        onClick(item)
                    }
                }
            }
        ),
        subtitle = item.artists?.firstOrNull()?.name ?: "Álbum"
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistHomeItem(item: PlaylistItem, onClick: (YTItem) -> Unit) {
    val playerViewModel = LocalPlayerViewModel.current
    val scope = rememberCoroutineScope()

    BaseHomeItem(
        item = item,
        onClick = onClick,
        imageShape = RoundedCornerShape(10.dp),
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.PLAYLIST,
        centerPlayVisible = false,
        contextMenuEnabled = false,
        onMoreClick = { onClick(item) },
        quickPlay = QuickPlayConfig(
            size = 42.dp,
            iconSize = 20.dp,
            onClick = {
                scope.launch {
                    val page = YouTube.playlist(item.id).getOrNull()
                    val songs = page?.songs.orEmpty()
                    if (songs.isNotEmpty()) {
                        playerViewModel.playPlaylist(
                            songs = songs,
                            startIndex = 0,
                            playlistId = item.id,
                            title = item.title
                        )
                    } else {
                        onClick(item)
                    }
                }
            }
        ),
        subtitle = item.author?.name ?: "Lista"
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ArtistHomeItem(item: ArtistItem, onClick: (YTItem) -> Unit) {
    BaseHomeItem(
        item = item,
        onClick = onClick,
        imageShape = CircleShape,
        alignment = Alignment.CenterHorizontally,
        titleAlign = TextAlign.Center,
        placeholderType = PlaceholderType.ARTIST,
        centerPlayVisible = false,
        contextMenuEnabled = false,
        onMoreClick = { onClick(item) },
        subtitle = "Artista"
    )
}

private data class QuickPlayConfig(
    val size: Dp,
    val iconSize: Dp,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BaseHomeItem(
    item: YTItem,
    onClick: (YTItem) -> Unit,
    imageShape: Shape,
    alignment: Horizontal,
    titleAlign: TextAlign,
    placeholderType: PlaceholderType,
    centerPlayVisible: Boolean,
    contextMenuEnabled: Boolean,
    onContextMenuAction: (DpOffset) -> Unit = {},
    onMoreClick: (() -> Unit)? = null,
    quickPlay: QuickPlayConfig? = null,
    subtitle: String,
    topStartOverlay: (@Composable BoxScope.() -> Unit)? = null,
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    val isArtist = item is ArtistItem
    val cardHeight = 180.dp
    val aspectRatio = item.thumbnailAspectRatio()
    val cardWidth = cardHeight * aspectRatio
    val contentPadding = 10.dp

    var isHovered by remember { mutableStateOf(false) }

    val overlayAlpha by animateFloatAsState(if (isHovered) 0.38f else 0f, tween(160), label = "overlay")
    val playIconAlpha by animateFloatAsState(if (isHovered && centerPlayVisible) 1f else 0f, tween(160), label = "play")
    val playIconScale by animateFloatAsState(
        if (isHovered && centerPlayVisible) 1f else 0.7f,
        spring(Spring.DampingRatioMediumBouncy),
        label = "playScale"
    )
    val menuBtnAlpha by animateFloatAsState(if (isHovered) 1f else 0f, tween(120), label = "menuBtn")
    val quickPlayAlpha by animateFloatAsState(
        if (isHovered && quickPlay != null) 1f else 0f,
        tween(120),
        label = "quickPlayBtn"
    )

    Box(modifier = Modifier.width(cardWidth + contentPadding * 2).padding(contentPadding)) {
        Column(horizontalAlignment = alignment) {
            BoxForContainerContextMenuItem(
                modifier = Modifier
                    .height(cardHeight)
                    .width(cardWidth)
                    .clip(RoundedCornerShape(12.dp))
                    .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                    .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                    .clickable { onClick(item) }
                    .pointerHoverIcon(PointerIcon.Hand),
                enabled = contextMenuEnabled,
                onHoverChange = { isHovered = it },
                onMenuAction = onContextMenuAction
            ) { menuButtonModifier, openMenuFromButton ->
                MelodistImage(
                    url = item.thumbnail,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = imageShape,
                    placeholderType = placeholderType,
                    iconSize = if (isArtist) 56.dp else 40.dp,
                    contentScale = ContentScale.Crop,
                    alignment = if (isArtist) Alignment.TopCenter else Alignment.Center
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = overlayAlpha }
                        .background(Color.Black)
                )

                if (centerPlayVisible) {
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            "Reproducir",
                            tint = Color.White,
                            modifier = Modifier
                                .size(56.dp)
                                .graphicsLayer {
                                    alpha = playIconAlpha
                                    scaleX = playIconScale
                                    scaleY = playIconScale
                                }
                        )
                    }
                }

                HoverCornerActionButton(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "Opciones",
                    onClick = { onMoreClick?.invoke() ?: openMenuFromButton() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .graphicsLayer { alpha = menuBtnAlpha },
                    buttonModifier = menuButtonModifier.pointerHoverIcon(PointerIcon.Hand),
                    visible = isHovered,
                    onButtonHoverChange = { if (it) isHovered = true }
                )

                if (quickPlay != null) {
                    HoverCornerActionButton(
                        icon = Icons.Rounded.PlayArrow,
                        contentDescription = "Reproducir",
                        onClick = quickPlay.onClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .graphicsLayer { alpha = quickPlayAlpha },
                        buttonModifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        visible = isHovered,
                        size = quickPlay.size,
                        iconSize = quickPlay.iconSize,
                        onButtonHoverChange = { if (it) isHovered = true }
                    )
                }

                topStartOverlay?.invoke(this)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = titleAlign
            )

            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                var subtitleHover by remember { mutableStateOf(false) }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = if (subtitleHover) TextDecoration.Underline else TextDecoration.None,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerHoverIcon(PointerIcon.Hand)
                        .onHover { subtitleHover = it },
                    textAlign = titleAlign
                )
            }
        }

        overlayContent()
    }
}