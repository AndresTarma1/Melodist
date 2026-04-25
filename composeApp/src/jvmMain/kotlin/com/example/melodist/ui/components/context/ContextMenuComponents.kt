package com.example.melodist.ui.components.context

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.melodist.player.DownloadState
import com.example.melodist.ui.components.song.AddToPlaylistDialog
import com.example.melodist.ui.components.song.rememberContextMenuPositionProvider
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.LibraryPlaylistsViewModel
import com.metrolist.innertube.models.SongItem
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CollectionContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String,
    isPlaylist: Boolean,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
    offset: DpOffset = DpOffset.Zero,
) {
    val positionProvider = rememberContextMenuPositionProvider(offset)

    if (!expanded) return

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(320.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                StyledMenuItem(
                    text = "Abrir ${if (isPlaylist) "playlist" else "album"}",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = onOpen
                )

                onPlay?.let {
                    StyledMenuItem(
                        text = "Reproducir",
                        icon = Icons.Default.PlayArrow,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = it
                    )
                }

                onShuffle?.let {
                    StyledMenuItem(
                        text = "Reproducir en aleatorio",
                        icon = Icons.Default.Shuffle,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = it
                    )
                }

                onRemoveFromLibrary?.let {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    StyledMenuItem(
                        text = "Eliminar de biblioteca",
                        icon = Icons.Default.Delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = it
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SongContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    song: SongItem,
    onRemoveFromLibrary: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    isLocalPlaylist: Boolean = false,
    showQueueActions: Boolean = true,
    offset: DpOffset = DpOffset.Zero
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)
    val playerViewModel = LocalPlayerViewModel.current
    val playlistsViewModel: LibraryPlaylistsViewModel = koinInject()
    val positionProvider = rememberContextMenuPositionProvider(offset)

    var showPlaylistDialog by remember { mutableStateOf(false) }

    if (expanded) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(
                focusable = true,
                dismissOnClickOutside = true,
                dismissOnBackPress = true,
            ),
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.width(320.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(vertical = 8.dp)
                ) {
                    when (downloadState) {
                        is DownloadState.Completed -> StyledMenuItem(
                            "Eliminar descarga",
                            Icons.Default.DeleteOutline,
                            MaterialTheme.colorScheme.error
                        ) { downloadViewModel.removeDownload(song.id) }

                        is DownloadState.Downloading, is DownloadState.Queued -> StyledMenuItem(
                            "Cancelar descarga",
                            Icons.Default.Cancel,
                            MaterialTheme.colorScheme.error
                        ) { downloadViewModel.cancelDownload(song.id) }

                        else -> StyledMenuItem(
                            "Descargar",
                            Icons.Default.Download,
                            MaterialTheme.colorScheme.primary
                        ) { downloadViewModel.downloadSong(song) }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    if (showQueueActions) {
                        StyledMenuItem("Reproducir a continuación", Icons.AutoMirrored.Filled.PlaylistAdd) {
                            playerViewModel.playNextResolved(song)
                            onDismiss()
                        }
                        StyledMenuItem("Agregar al final de la cola", Icons.AutoMirrored.Filled.QueueMusic) {
                            playerViewModel.addToQueueResolved(song)
                            onDismiss()
                        }
                    }

                    StyledMenuItem("Añadir a playlist local", Icons.Default.AddCircleOutline) {
                        showPlaylistDialog = true
                        onDismiss()
                    }

                    onRemoveFromLibrary?.let {
                        StyledMenuItem(
                            "Eliminar de biblioteca",
                            Icons.Default.Delete,
                            MaterialTheme.colorScheme.error,
                            onClick = it
                        )
                    }

                    if (isLocalPlaylist && onRemoveFromPlaylist != null) {
                        StyledMenuItem(
                            "Quitar de esta playlist",
                            Icons.Default.RemoveCircleOutline,
                            MaterialTheme.colorScheme.error,
                            onClick = onRemoveFromPlaylist
                        )
                    }
                }
            }
        }
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            song = song,
            playlistsViewModel = playlistsViewModel,
            onDismiss = { showPlaylistDialog = false }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StyledMenuItem(
    text: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .background(
                if (hovered) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                else Color.Transparent
            )
            .clickable { onClick() }
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
