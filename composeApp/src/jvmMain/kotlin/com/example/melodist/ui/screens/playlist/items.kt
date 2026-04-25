package com.example.melodist.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import com.example.melodist.ui.components.BoxForContainerContextMenuItem
import com.example.melodist.ui.components.song.DownloadIndicator
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.context.SongContextMenu
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.ui.screens.shared.formatDuration
import com.metrolist.innertube.models.SongItem

val ListItemHeight = 64.dp
val ListThumbnailSize = 48.dp
val ThumbnailCornerRadius = 6.dp

@Composable
inline fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    noinline subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    isAvailable: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (isActive) {
            modifier // playing highlight
                .height(ListItemHeight)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    color = // selected active
                        if (isSelected == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.secondaryContainer
                )
        } else if (isSelected == true) {
            modifier // inactive selected
                .height(ListItemHeight)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.4f))
        } else {
            modifier // default
                .height(ListItemHeight)
                .padding(horizontal = 8.dp)
        }
    ) {
        Box(
            modifier = Modifier.padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            thumbnailContent()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    subtitle()
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            trailingContent()
        }
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
) = ListItem(
    title = title,
    subtitle = {
        badges()

        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SongListItem(
    albumIndex: Int? = null,
    song: SongItem,
    onPlay: () -> Unit,
    isSelected: Boolean = false,
    isLocalPlaylist: Boolean = false,
    onRemoveFromPlaylist: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)

    var isHovered by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

    Box(modifier = modifier) {
        BoxForContainerContextMenuItem(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            onHoverChange = { isHovered = it },
            onMenuAction = { offset ->
                menuOffset = offset
                showContextMenu = true
            }
        ) { menuButtonModifier, openMenuFromButton ->
            Surface(
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    title = song.title,
                    subtitle = song.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" },
                    badges = {
                        if (song.explicit) {
                            Icon(
                                Icons.Default.Explicit, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    },
                    thumbnailContent = {
                        Box(
                            modifier = Modifier.width(ListThumbnailSize)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onPlay() },
                            contentAlignment = Alignment.Center
                        ) {

                            if (albumIndex != null) {
                                Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                    if (isHovered) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            text = albumIndex.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(ListThumbnailSize)
                                        .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                                        .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                                ) {
                                    MelodistImage(
                                        url = song.thumbnail,
                                        contentDescription = song.title,
                                        modifier = Modifier.matchParentSize(),
                                        shape = RoundedCornerShape(ThumbnailCornerRadius),
                                        placeholderType = PlaceholderType.SONG,
                                        iconSize = 20.dp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                Color.Black.copy(alpha = if (isHovered) 0.4f else 0f),
                                                shape = RoundedCornerShape(ThumbnailCornerRadius)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White.copy(alpha = if (isHovered) 1f else 0f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            if (isHovered) {
                                // Estado hover: mostrar acciones
                                IconButton(
                                    onClick = { /* TODO: Dislike */ },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.ThumbDown,
                                        null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { /* TODO: Like */ },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.ThumbUp,
                                        null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = openMenuFromButton,
                                    modifier = menuButtonModifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {}
                                )
                            } else {
                                // Estado normal: duración y descarga
                                DownloadIndicator(
                                    state = downloadState,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                // Duración con ancho mínimo consistente
                                Text(
                                    text = formatDuration(song.duration ?: 0),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.widthIn(min = 40.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isSelected = isSelected
                )
            }
        }

        SongContextMenu(
            expanded = showContextMenu,
            isLocalPlaylist = isLocalPlaylist,
            onRemoveFromPlaylist = { onRemoveFromPlaylist?.invoke(song.id) },
            onDismiss = { showContextMenu = false },
            song = song,
            offset = menuOffset
        )
    }
}

