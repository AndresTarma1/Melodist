package com.example.melodist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.melodist.player.DownloadState
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalSnackbarHostState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalLibraryViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.LibraryPlaylistsViewModel
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DownloadIndicator(
    state: DownloadState?,
    modifier: Modifier = Modifier
) {
    when (state) {
        is DownloadState.Queued -> Icon(
            Icons.Default.HourglassTop,
            contentDescription = "En cola",
            modifier = modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is DownloadState.Downloading -> Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { if (state.progress >= 0f) state.progress else 0f },
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
        }
        is DownloadState.Completed -> Icon(
            Icons.Default.DownloadDone,
            contentDescription = "Descargada",
            modifier = modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        is DownloadState.Failed -> Icon(
            Icons.Default.ErrorOutline,
            contentDescription = "Error de descarga",
            modifier = modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error
        )
        else -> Unit
    }
}

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

    var showPlaylistDialog by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
    ) {
        @Composable
        fun StyledMenuItem(
            text: String,
            icon: ImageVector,
            iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick: () -> Unit
        ) {
            DropdownMenuItem(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp)),
                text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
                onClick = {
                    onClick()
                    onDismiss()
                },
                leadingIcon = { Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp)) }
            )
        }

        // --- SECCIÓN DE DESCARGAS ---
        when (downloadState) {
            is DownloadState.Completed -> StyledMenuItem(
                "Eliminar descarga", Icons.Default.DeleteOutline, MaterialTheme.colorScheme.error
            ) { downloadViewModel.removeDownload(song.id) }

            is DownloadState.Downloading, is DownloadState.Queued -> StyledMenuItem(
                "Cancelar descarga", Icons.Default.Cancel, MaterialTheme.colorScheme.error
            ) { downloadViewModel.cancelDownload(song.id) }

            else -> StyledMenuItem(
                "Descargar", Icons.Default.Download, MaterialTheme.colorScheme.primary
            ) { downloadViewModel.downloadSong(song) }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        // --- SECCIÓN DE COLA ---
        if (showQueueActions) {
            StyledMenuItem("Reproducir a continuación", Icons.AutoMirrored.Filled.PlaylistAdd) {
                playerViewModel.playNext(song)
            }
            StyledMenuItem("Agregar al final de la cola", Icons.AutoMirrored.Filled.QueueMusic) {
                playerViewModel.addToQueue(song)
            }
        }

        // --- SECCIÓN DE BIBLIOTECA/PLAYLIST ---
        StyledMenuItem("Añadir a playlist", Icons.Default.AddCircleOutline) {
            showPlaylistDialog = true
        }

        onRemoveFromLibrary?.let {
            StyledMenuItem("Eliminar de biblioteca", Icons.Default.Delete, MaterialTheme.colorScheme.error, it)
        }

        if (isLocalPlaylist && onRemoveFromPlaylist != null) {
            StyledMenuItem("Quitar de esta playlist", Icons.Default.RemoveCircleOutline, MaterialTheme.colorScheme.error, onRemoveFromPlaylist)
        }
    }

    if (showPlaylistDialog) {
//        AddToPlaylistDialog(
//            song = song,
//            playlistsViewModel = playlistsViewModel,
//            onDismiss = { showPlaylistDialog = false }
//        )
    }
}