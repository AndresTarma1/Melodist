package com.example.melodist.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.melodist.viewmodels.PlayerProgressState
import com.example.melodist.viewmodels.PlayerUiState

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    progressState: PlayerProgressState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClickExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    MiniPlayerContent(
        state = state,
        progressState = progressState,
        onTogglePlayPause = onTogglePlayPause,
        onNext = onNext,
        onPrevious = onPrevious,
        onClickExpand = onClickExpand,
        modifier = modifier
    )
}