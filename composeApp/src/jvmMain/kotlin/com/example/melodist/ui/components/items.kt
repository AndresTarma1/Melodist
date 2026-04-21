package com.example.melodist.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Horizontal
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.melodist.ui.components.context.SongContextMenu
import com.example.melodist.ui.helpers.contextMenuArea
import com.example.melodist.utils.thumbnailAspectRatio
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import org.jetbrains.jewel.foundation.modifier.onHover

enum class ItemContentSource {
    LOCAL,
    YOUTUBE,
}

data class CornerQuickPlayConfig(
    val size: Dp,
    val iconSize: Dp,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun YouTubeGridItem(
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
    quickPlay: CornerQuickPlayConfig? = null,
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaGridItem(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    placeholderType: PlaceholderType,
    shape: Shape,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onRemove: () -> Unit = {},
    isRemovable: Boolean = true,
    source: ItemContentSource = ItemContentSource.LOCAL,
) {
    val isCircle = shape == CircleShape
    val alignment = if (isCircle) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (isCircle) TextAlign.Center else TextAlign.Start
    var isImageHovered by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val showImageActions = placeholderType == PlaceholderType.ALBUM || placeholderType == PlaceholderType.PLAYLIST
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isImageHovered && showImageActions) 0.32f else 0f,
        animationSpec = tween(140),
        label = "libraryOverlay"
    )
    val menuAlpha by animateFloatAsState(
        targetValue = if (isImageHovered && showImageActions) 1f else 0f,
        animationSpec = tween(120),
        label = "libraryMenu"
    )
    val playAlpha by animateFloatAsState(
        targetValue = if (isImageHovered && onPlay != null && showImageActions) 1f else 0f,
        animationSpec = tween(120),
        label = "libraryPlay"
    )
    val sourceIcon = if (source == ItemContentSource.LOCAL) Icons.Default.PhoneAndroid else Icons.Default.CloudDone

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .padding(8.dp),
            horizontalAlignment = alignment
        ) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .onPointerEvent(PointerEventType.Enter) { isImageHovered = true }
                    .onPointerEvent(PointerEventType.Exit) { isImageHovered = false }
            ) {
                MelodistImage(
                    url = thumbnailUrl,
                    contentDescription = title,
                    modifier = Modifier.aspectRatio(1f).fillMaxWidth(),
                    shape = shape,
                    placeholderType = placeholderType,
                    iconSize = 40.dp,
                    contentScale = ContentScale.Fit,
                    alignment = if (isCircle) Alignment.TopCenter else Alignment.Center
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Color.Black.copy(alpha = overlayAlpha),
                            RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                )

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.50f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = sourceIcon,
                            contentDescription = if (source == ItemContentSource.LOCAL) "Local" else "YouTube Music",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (showImageActions) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                            .padding(4.dp)
                            .graphicsLayer { alpha = menuAlpha }
                    ) {
                        Icon(Icons.Default.MoreVert, "Opciones", modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.9f))
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (isRemovable) {
                                DropdownMenuItem(
                                    text = { Text("Eliminar de la biblioteca") },
                                    onClick = { onRemove(); showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Menu proximamente") },
                                    onClick = { showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.MoreHoriz, null) }
                                )
                            }
                        }
                    }
                }

                if (onPlay != null && showImageActions) {
                    FilledIconButton(
                        onClick = onPlay,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(34.dp)
                            .graphicsLayer { alpha = playAlpha },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.55f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir", modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = textAlign
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = textAlign
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun YoutubeListItem(
    item: YTItem,
    source: ItemContentSource,
    onItemClick: (YTItem) -> Unit,
) {
    val shape = when (item) {
        is ArtistItem -> CircleShape
        is PlaylistItem -> RoundedCornerShape(8.dp)
        else -> RoundedCornerShape(6.dp)
    }

    val imageSize = when (item) {
        is PlaylistItem, is ArtistItem -> 56.dp
        else -> 48.dp
    }

    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var isHovered by remember { mutableStateOf(false) }
    var buttonPosition by remember { mutableStateOf(Offset.Zero) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val density = LocalDensity.current
    val sourceIcon = if (source == ItemContentSource.LOCAL) Icons.Default.PhoneAndroid else Icons.Default.CloudDone

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .onGloballyPositioned { rootCoordinates = it }
            .background(if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent)
            .clickable { onItemClick(item) }
            .pointerHoverIcon(PointerIcon.Hand)
            .contextMenuArea(
                enabled = item is SongItem,
                onHoverChange = { isHovered = it },
                onMenuAction = { offset ->
                    menuOffset = offset
                    showMenu = true
                }
            )
    ) {
        androidx.compose.material3.ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            supportingContent = {
                val subtitle = when (item) {
                    is SongItem -> {
                        val artists = item.artists.joinToString { it.name }
                        val album = item.album?.name?.let { " • $it" } ?: ""
                        "$artists$album"
                    }
                    is AlbumItem -> {
                        val artists = item.artists?.joinToString { it.name } ?: "Álbum"
                        "Álbum • $artists"
                    }
                    is ArtistItem -> "Artista"
                    is PlaylistItem -> {
                        val author = item.author?.name?.let { " • $it" } ?: ""
                        "Playlist$author"
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(16.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(sourceIcon, null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            leadingContent = {
                MelodistImage(
                    url = item.thumbnail,
                    contentDescription = item.title,
                    modifier = Modifier.size(imageSize),
                    shape = shape,
                    placeholderType = when (item) {
                        is ArtistItem -> PlaceholderType.ARTIST
                        is AlbumItem -> PlaceholderType.ALBUM
                        is PlaylistItem -> PlaceholderType.PLAYLIST
                        else -> PlaceholderType.SONG
                    },
                    contentScale = ContentScale.Crop,
                    iconSize = if (item is PlaylistItem) 28.dp else 24.dp
                )
            },
            trailingContent = {
                if (item is SongItem) {
                    IconButton(
                        onClick = {
                            menuOffset = with(density) {
                                DpOffset(buttonPosition.x.toDp(), buttonPosition.y.toDp())
                            }
                            showMenu = true
                        },
                        modifier = Modifier.onGloballyPositioned { buttonCoords ->
                            rootCoordinates.let { root ->
                                buttonPosition = root?.localPositionOf(buttonCoords, Offset.Zero) ?: Offset.Zero
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Más opciones",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )

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


