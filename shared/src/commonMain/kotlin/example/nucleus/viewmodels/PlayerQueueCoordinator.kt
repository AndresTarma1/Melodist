package example.nucleus.viewmodels

import example.nucleus.models.MediaMetadata

object PlayerQueueCoordinator {

    fun singleSession(song: MediaMetadata): QueueSession =
        QueueSession(
            source = QueueSource.Single(song.id),
            items = listOf(song),
            order = listOf(0),
            currentIndex = 0
        )

    fun collectionSession(
        source: QueueSource,
        items: List<MediaMetadata>,
        startIndex: Int = 0
    ): QueueSession {
        if (items.isEmpty()) return QueueSession(source = source)
        val index = startIndex.coerceIn(0, items.lastIndex)
        return QueueSession(
            source = source,
            items = items,
            order = items.indices.toList(),
            currentIndex = index
        )
    }

    fun append(state: PlayerUiState, song: MediaMetadata): PlayerUiState {
        if (state.queueSession.items.isEmpty()) {
            val session = collectionSession(QueueSource.Custom, listOf(song))
            return state.copy(queueSession = session, queue = session.queueItems())
        }

        val session = state.queueSession
        val items = session.items + song
        val order = session.order + items.lastIndex
        val newSession = session.copy(items = items, order = order)
        return state.copy(
            queueSession = newSession,
            queue = newSession.queueItems()
        )
    }

    fun insertNext(state: PlayerUiState, song: MediaMetadata): PlayerUiState {
        if (state.queueSession.items.isEmpty()) {
            val session = collectionSession(QueueSource.Custom, listOf(song))
            return state.copy(queueSession = session, queue = session.queueItems())
        }

        val session = state.queueSession
        val items = session.items + song
        val insertedBaseIndex = items.lastIndex
        val insertAt = (state.currentIndex + 1).coerceAtMost(session.order.size)
        val newOrder = session.order.toMutableList().apply { add(insertAt, insertedBaseIndex) }
        val newSession = session.copy(items = items, order = newOrder)
        return state.copy(
            queueSession = newSession,
            queue = newSession.queueItems()
        )
    }

    fun toggleShuffle(state: PlayerUiState): PlayerUiState {
        val session = state.queueSession
        if (session.items.isEmpty()) return state

        return if (state.isShuffled) {
            val naturalOrder = session.naturalOrder()
            val currentId = state.currentSong?.id
            val newIndex = naturalOrder.indexOfFirst {
                session.items.getOrNull(it)?.id == currentId
            }.coerceAtLeast(0)
            val newSession = session.copy(order = naturalOrder, currentIndex = newIndex)
            state.copy(
                queue = newSession.queueItems(),
                currentIndex = newIndex,
                isShuffled = false,
                queueSession = newSession
            )
        } else {
            val currentItem = state.currentSong ?: return state
            val currentBaseIndex = session.items.indexOfFirst { it.id == currentItem.id }.coerceAtLeast(0)
            val rest = session.items.indices.filter { it != currentBaseIndex }.shuffled()
            val shuffledOrder = listOf(currentBaseIndex) + rest
            val newSession = session.copy(order = shuffledOrder, currentIndex = 0)
            state.copy(
                queue = newSession.queueItems(),
                currentIndex = 0,
                isShuffled = true,
                queueSession = newSession
            )
        }
    }

    fun shuffleFromStart(state: PlayerUiState): PlayerUiState {
        val session = state.queueSession
        if (session.items.isEmpty()) return state

        val shuffledOrder = session.items.indices.shuffled()
        val newSession = session.copy(order = shuffledOrder, currentIndex = 0)
        return state.copy(
            currentSong = newSession.currentSong(),
            queue = newSession.queueItems(),
            currentIndex = 0,
            isShuffled = true,
            queueSession = newSession
        )
    }

    fun move(state: PlayerUiState, fromIndex: Int, toIndex: Int): PlayerUiState {
        val session = state.queueSession
        if (fromIndex !in session.order.indices || toIndex !in session.order.indices) return state

        val newOrder = session.order.toMutableList()
        val movedItem = newOrder.removeAt(fromIndex)
        newOrder.add(toIndex, movedItem)

        val curIndex = state.currentIndex
        val newCurrentIndex = when {
            fromIndex == curIndex -> toIndex
            curIndex in (fromIndex + 1)..toIndex -> curIndex - 1
            curIndex in toIndex..<fromIndex -> curIndex + 1
            else -> curIndex
        }

        val newSession = session.copy(order = newOrder, currentIndex = newCurrentIndex)
        return state.copy(
            queueSession = newSession,
            queue = newSession.queueItems(),
            currentIndex = newCurrentIndex
        )
    }

    /**
     * @param ignoreRepeatOne si true (skip manual del usuario), avanza/retrocede como ALL/OFF
     * en lugar de devolver el índice actual (que re-carga la misma canción).
     */
    fun nextIndex(state: PlayerUiState, ignoreRepeatOne: Boolean = false): Int? {
        if (state.queueSession.order.isEmpty()) return null
        val mode = if (ignoreRepeatOne && state.repeatMode == RepeatMode.ONE) {
            RepeatMode.ALL
        } else {
            state.repeatMode
        }
        val size = state.queueSession.order.size
        val current = state.currentIndex.coerceIn(0, size - 1)

        return when (mode) {
            RepeatMode.ONE -> current
            RepeatMode.ALL -> (current + 1) % size
            RepeatMode.OFF -> {
                val next = current + 1
                if (next >= size) null else next
            }
        }
    }

    fun previousIndex(state: PlayerUiState, ignoreRepeatOne: Boolean = false): Int? {
        if (state.queueSession.order.isEmpty()) return null
        val mode = if (ignoreRepeatOne && state.repeatMode == RepeatMode.ONE) {
            RepeatMode.ALL
        } else {
            state.repeatMode
        }
        val size = state.queueSession.order.size
        val current = state.currentIndex.coerceIn(0, size - 1)

        return when (mode) {
            RepeatMode.ONE -> current
            RepeatMode.ALL -> if (current - 1 < 0) size - 1 else current - 1
            RepeatMode.OFF -> if (current <= 0) null else current - 1
        }
    }
}
