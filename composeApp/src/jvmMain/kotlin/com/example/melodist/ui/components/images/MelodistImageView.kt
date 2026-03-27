package com.example.melodist.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.example.melodist.data.AppPreferences

enum class PlaceholderType {
    SONG, ALBUM, ARTIST, PLAYLIST
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RectangleShape) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    )
}

@Composable
fun MelodistImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    placeholderType: PlaceholderType = PlaceholderType.SONG,
    iconSize: Dp = 32.dp,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center
) {
    val placeholderIcon: ImageVector = when (placeholderType) {
        PlaceholderType.SONG -> Icons.Default.MusicNote
        PlaceholderType.ALBUM -> Icons.Default.Album
        PlaceholderType.ARTIST -> Icons.Default.Person
        PlaceholderType.PLAYLIST -> Icons.Default.MusicNote
    }

    val placeholderTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val placeholderBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val imagesEnabled by AppPreferences.imagesEnabled.collectAsState()

    if (url.isNullOrBlank() || !imagesEnabled) {
        Box(
            modifier = modifier.clip(shape).background(placeholderBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = placeholderTint
            )
        }
    } else {
        val errorPlaceholder: @Composable () -> Unit = {
            Box(
                modifier = Modifier.fillMaxSize().background(placeholderBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = placeholderTint
                )
            }
        }


        SubcomposeAsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Fit,
            alignment = alignment,
            loading = { ShimmerBox(modifier = Modifier.fillMaxSize(), shape = shape) },
            error = { errorPlaceholder() }
        )
    }
}

