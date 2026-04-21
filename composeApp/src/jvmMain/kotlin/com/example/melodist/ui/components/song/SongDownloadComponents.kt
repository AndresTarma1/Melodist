package com.example.melodist.ui.components.song

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.melodist.player.DownloadState
import com.example.melodist.player.YTPlayerutils
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.LibraryPlaylistsViewModel
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@Composable
fun DownloadIndicator(
    state: DownloadState?,
    modifier: Modifier = Modifier
) {
    when (state) {
        is DownloadState.Queued -> Icon(
            Icons.Default.HourglassTop,
            contentDescription = "En cola",
            modifier = modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is DownloadState.Downloading -> Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { if (state.progress >= 0f) state.progress else 0f },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
        }
        is DownloadState.Completed -> Icon(
            Icons.Default.DownloadDone,
            contentDescription = "Descargada",
            modifier = modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        is DownloadState.Failed -> Icon(
            Icons.Default.ErrorOutline,
            contentDescription = "Error de descarga",
            modifier = modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.error
        )
        else -> Unit
    }
}

@Composable
fun rememberContextMenuPositionProvider(clickOffset: DpOffset): PopupPositionProvider {
    val density = LocalDensity.current
    val clickXPx = with(density) { clickOffset.x.toPx() }
    val clickYPx = with(density) { clickOffset.y.toPx() }
    val marginPx = with(density) { 6.dp.roundToPx() }

    return remember(clickXPx, clickYPx, marginPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                var x = anchorBounds.left + clickXPx.roundToInt()
                var y = anchorBounds.top + clickYPx.roundToInt()

                if (x + popupContentSize.width > windowSize.width - marginPx) {
                    x = windowSize.width - popupContentSize.width - marginPx
                }
                if (y + popupContentSize.height > windowSize.height - marginPx) {
                    y -= popupContentSize.height
                }

                x = x.coerceAtLeast(marginPx)
                y = y.coerceAtLeast(marginPx)

                return IntOffset(x, y)
            }
        }
    }
}

suspend fun SongItem.withMissingMetadataResolved(): SongItem {
    val hasDuration = duration != null
    if (hasDuration) return this

    val playbackData = withContext(Dispatchers.IO) {
        YTPlayerutils.playerResponseForMetadata(id).getOrNull()
    }
    val resolvedDuration = playbackData?.videoDetails?.lengthSeconds?.toIntOrNull()

    return if (resolvedDuration != null && resolvedDuration > 0) {
        copy(duration = resolvedDuration)
    } else {
        this
    }
}



@Composable
fun AddToPlaylistDialog(
    song: SongItem,
    playlistsViewModel: LibraryPlaylistsViewModel,
    onDismiss: () -> Unit
) {
    val localPlaylists by playlistsViewModel.localPlaylists.collectAsState()
    var newPlaylistName by remember { mutableStateOf("") }
    var isCreatingNew by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isCreatingNew) "Crear nueva playlist" else "Añadir a playlist",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            if (isCreatingNew) {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nombre de la playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (localPlaylists.isEmpty()) {
                Text(
                    "No tienes playlists locales creadas todavía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp, max = 260.dp)
                ) {
                    items(localPlaylists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    playlistsViewModel.addSongToLocalPlaylist(playlist.id, song)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = playlist.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreatingNew) {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            playlistsViewModel.createLocalPlaylist(newPlaylistName.trim(), song)
                            onDismiss()
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Crear y añadir")
                }
            } else {
                Button(
                    onClick = { isCreatingNew = true }
                ) {
                    Text("Nueva playlist")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isCreatingNew) {
                        isCreatingNew = false
                        newPlaylistName = ""
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (isCreatingNew) "Atrás" else "Cancelar")
            }
        }
    )
}
