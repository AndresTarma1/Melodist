@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.navigation.Route
import example.nucleus.ui.components.*
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.layout.HorizontalScrollableRow
import example.nucleus.ui.screens.shared.SectionGridItem
import example.nucleus.ui.screens.shared.SectionListItem
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.ctaLabel
import example.nucleus.ui.themes.expressiveFadeDuration
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.ui.themes.expressiveTween
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.viewmodels.ArtistState
import example.nucleus.viewmodels.ArtistManagerViewModel
import example.nucleus.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.*
import com.metrolist.innertube.pages.ArtistPage
import example.nucleus.shared.generated.resources.Res
import example.nucleus.shared.generated.resources.*
import example.nucleus.utils.upscaleThumbnailUrl
import org.jetbrains.compose.resources.stringResource

@Composable
fun ArtistScreenRoute(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistManagerViewModel,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val uiState by viewModel.uiState.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val success = uiState as? ArtistState.Success

    ArtistScreen(
        uiState = uiState,
        playerViewModel = playerViewModel,
        onNavigate = onNavigate,
        onBack = onBack,
        isSaved = isSaved,
        actions = remember(viewModel, playerViewModel, success) {
            ArtistScreenActions(
                onToggleSave = { viewModel.toggleSave() },
                onPlayArtist = {
                    val endpoint = success?.artistPage?.artist?.playEndpoint
                        ?: return@ArtistScreenActions
                    playerViewModel.playEndpoint(endpoint)
                },
                onShuffleArtist = {
                    val endpoint = success?.artistPage?.artist?.shuffleEndpoint ?: return@ArtistScreenActions
                    playerViewModel.playEndpoint(endpoint)
                },
                onRadioArtist = {
                    val endpoint = success?.artistPage?.artist?.radioEndpoint
                        ?: return@ArtistScreenActions
                    playerViewModel.playEndpoint(endpoint)
                }
            )
        }
    )
}

data class ArtistScreenActions(
    val onToggleSave: () -> Unit,
    val onPlayArtist: () -> Unit,
    val onShuffleArtist: () -> Unit,
    val onRadioArtist: () -> Unit = {},
)


@Composable
fun ArtistScreen(
    uiState: ArtistState,
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    isSaved: Boolean = false,
    actions: ArtistScreenActions,
    playerViewModel: PlayerViewModel
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                    onBack()
                    true
                } else {
                    false
                }
            }
    ) {
        when (uiState) {
            is ArtistState.Loading -> ArtistScreenSkeleton()
            is ArtistState.Success -> ArtistScreenContent(uiState.artistPage, onNavigate, isSaved, actions, playerViewModel)
            is ArtistState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// Contenido principal
@Composable
private fun ArtistScreenContent(
    artistPage: ArtistPage,
    onNavigate: (Route) -> Unit,
    isSaved: Boolean,
    actions: ArtistScreenActions,
    playerViewModel: PlayerViewModel
) {
    val lazyListState = rememberLazyListState()
    val surface = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.fillMaxSize().background(surface))

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current),
        ) {
            item(key = "banner") {
                ArtistBanner(
                    artistPage = artistPage,
                    isSaved = isSaved,
                    actions = actions,
                    surfaceColor = surface
                )
            }

            itemsIndexed(
                artistPage.sections,
            ) { index , section ->

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 10.dp, end = 12.dp))
                {

                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )

                    if (index == 0) {
                        Column {
                            section.items.forEach { item ->
                                SectionListItem(
                                    item = item,
                                    onNavigate = onNavigate,
                                    playerViewModel = playerViewModel
                                )
                            }
                        }

                    } else {
                        HorizontalScrollableRow(
                            state = rememberLazyListState()
                        ) {

                            items(
                                section.items,
                                key = { it.id }
                            ) { item ->
                                SectionGridItem(
                                    item = item,
                                    onNavigate = onNavigate,
                                    playerViewModel = playerViewModel
                                )
                            }
                        }
                    }
                }
            }
        }

        AppVerticalScrollbar(
            state = lazyListState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

// BANNER — imagen con gradientes multicapa al estilo YouTube Music
@Composable
fun ArtistBanner(
    artistPage: ArtistPage,
    isSaved: Boolean,
    actions: ArtistScreenActions,
    surfaceColor: Color,
    modifier: Modifier = Modifier // Añadido para mejor reutilización
) {
    var descExpanded by remember { mutableStateOf(false) }

    // Evita recalcular en cada recomposición
    val hasPlayable = remember(artistPage.sections) {
        artistPage.sections.any { s -> s.items.any { it is SongItem } }
    }

    // Aumentamos la resolución a 1440 para que no se pixele en monitores grandes
    val urlImage = upscaleThumbnailUrl(artistPage.artist.thumbnail, 1200)

    // Memorizamos los gradientes para ahorrar CPU
    val verticalGradient = remember(surfaceColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.28f to Color.Transparent,
                0.52f to surfaceColor.copy(alpha = 0.20f),
                0.70f to surfaceColor.copy(alpha = 0.62f),
                0.84f to surfaceColor.copy(alpha = 0.88f),
                1.00f to surfaceColor
            )
        )
    }

    val horizontalGradient = remember {
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to Color.Black.copy(alpha = 0.55f),
                1.00f to Color.Transparent
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(520.dp) // Permite que el contenedor crezca si es necesario
            .animateContentSize(if (LocalAnimationsEnabled.current) expressiveTween(expressiveFadeDuration) else snap()) // Suaviza el cambio de tamaño al expandir el texto
    ) {
        MusicPlayerImage(
            url = urlImage,
            contentDescription = artistPage.artist.title,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            placeholderType = PlaceholderType.ARTIST,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            // La URL se pide a 1200px; sin override MusicPlayerImage decodifica a 384px y se ve borrosa
            // al estirarse al banner (igual que hace Metrolist con AsyncImage + resize(1200, 1200)).
            coilSizeOverride = 1200,
        )

        // Capa de gradiente vertical
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(verticalGradient)
        )

        // Capa de gradiente horizontal (sombra para el texto)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(horizontalGradient)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 32.dp)
        ) {
            Text(
                text = artistPage.artist.title,
                style = MaterialTheme.typography.displayLargeEmphasized,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            artistPage.monthlyListenerCount?.let { listeners ->
                Text(
                    text = listeners,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(8.dp))
            }

            artistPage.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                        val animationsEnabled = LocalAnimationsEnabled.current
                        AnimatedContent(
                            targetState = descExpanded,
                            transitionSpec = {
                                if (animationsEnabled) {
                                    fadeIn(expressiveFadeTween()) togetherWith fadeOut(expressiveTween(150))
                                } else {
                                    EnterTransition.None togetherWith ExitTransition.None
                                }
                            },
                            label = "descAnim"
                        ) { expanded ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (desc.length > 120) {
                            TextButton(
                                onClick = { descExpanded = !descExpanded },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(
                                    text = if (descExpanded) stringResource(Res.string.less) else stringResource(Res.string.more),
                                    style = MaterialTheme.typography.labelMediumEmphasized,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // Fila de botones (sin cambios lógicos, pero ahora dentro de un contenedor más seguro)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { if (hasPlayable) actions.onShuffleArtist() },
                    enabled = hasPlayable,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    shape = circleAwareShape(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.shuffle), style = MaterialTheme.typography.ctaLabel)
                }

                Button(
                    onClick = { if (hasPlayable) actions.onRadioArtist() },
                    enabled = hasPlayable,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    shape = circleAwareShape(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(Icons.Rounded.Radio, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.radio_text), style = MaterialTheme.typography.ctaLabel)
                }

                val subscribedText = stringResource(Res.string.subscribed)
                val subscribeText = stringResource(Res.string.subscribe_text)
                val subLabel = buildString {
                    append(if (isSaved) subscribedText else subscribeText)
                    artistPage.subscriberCountText?.let { append("  $it") }
                }

                OutlinedButton(
                    onClick = actions.onToggleSave,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                        containerColor = if (isSaved) Color.White.copy(alpha = 0.15f) else Color.Transparent
                    ),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = Color.White.copy(alpha = if (isSaved) 0.6f else 0.85f)
                    ),
                    shape = circleAwareShape(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    if (isSaved) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(subLabel, fontWeight = FontWeight.Medium)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(circleAwareShape())
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { /* TODO */ }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MoreVert, stringResource(Res.string.more_options), tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
