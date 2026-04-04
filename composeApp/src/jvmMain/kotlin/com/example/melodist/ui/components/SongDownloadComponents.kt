package com.example.melodist.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.melodist.player.DownloadState
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.metrolist.innertube.models.SongItem

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    song: SongItem,
    onRemoveFromLibrary: (() -> Unit)? = null,
    offset: DpOffset = DpOffset.Zero
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)
    val playerViewModel = LocalPlayerViewModel.current

    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(24.dp))
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            offset = offset,
        ) {
            @Composable
            fun StyledMenuItem(
                text: String,
                icon: ImageVector,
                iconTint: Color,
                onClick: () -> Unit
            ) {
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    text = { Text(text) },
                    onClick = { onClick(); onDismiss() },
                    leadingIcon = { Icon(icon, null, tint = iconTint) }
                )
            }

            when (downloadState) {
                is DownloadState.Completed -> StyledMenuItem(
                    "Eliminar descarga",
                    Icons.Default.DeleteOutline,
                    MaterialTheme.colorScheme.error,
                    { downloadViewModel.removeDownload(song.id) }
                )
                is DownloadState.Downloading, is DownloadState.Queued -> StyledMenuItem(
                    "Cancelar descarga",
                    Icons.Default.Cancel,
                    MaterialTheme.colorScheme.error,
                    { downloadViewModel.cancelDownload(song.id) }
                )
                else -> StyledMenuItem(
                    "Descargar",
                    Icons.Default.Download,
                    MaterialTheme.colorScheme.primary
                ) { downloadViewModel.downloadSong(song) }
            }

            StyledMenuItem(
                "Reproducir a continuación",
                Icons.AutoMirrored.Filled.PlaylistAdd,
                MaterialTheme.colorScheme.primary
            ) { playerViewModel.playNext(song) }

            StyledMenuItem(
                "Agregar al final de la cola",
                Icons.AutoMirrored.Filled.QueueMusic,
                MaterialTheme.colorScheme.primary
            ) { playerViewModel.addToQueue(song) }

            onRemoveFromLibrary?.let { remove ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp))
                StyledMenuItem(
                    "Eliminar de la biblioteca",
                    Icons.Default.Delete,
                    MaterialTheme.colorScheme.error,
                    remove
                )
            }
        }
    }
}
