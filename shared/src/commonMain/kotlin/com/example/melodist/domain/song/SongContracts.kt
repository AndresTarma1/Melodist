package com.example.melodist.domain.song

import com.example.melodist.domain.error.toAppError
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.Flow
import com.metrolist.innertube.models.YTItem

interface SongRepository {
    fun getSavedSongs(): Flow<List<SongItem>>
    fun isSongSaved(id: String): Flow<Boolean>
    suspend fun saveSong(song: SongItem)
    suspend fun removeSong(id: String)
    suspend fun getDownloadedSongs(): List<SongItem>
}

interface SongLikeService {
    suspend fun likeSong(songId: String): Result<Unit>
    suspend fun dislikeSong(songId: String): Result<Unit>
    suspend fun removeLike(songId: String): Result<Unit>
}

class GetSavedSongsUseCase(
    private val repository: SongRepository,
) {
    operator fun invoke(): Flow<List<SongItem>> = repository.getSavedSongs()
}

class IsSongSavedUseCase(
    private val repository: SongRepository,
) {
    operator fun invoke(id: String): Flow<Boolean> = repository.isSongSaved(id)
}

class SaveSongUseCase(
    private val repository: SongRepository,
) {
    suspend operator fun invoke(song: SongItem) {
        try {
            repository.saveSong(song)
        } catch (e: Exception) {
            throw e.toAppError()
        }
    }
}

class RemoveSongUseCase(
    private val repository: SongRepository,
) {
    suspend operator fun invoke(id: String) {
        try {
            repository.removeSong(id)
        } catch (e: Exception) {
            throw e.toAppError()
        }
    }
}

class GetDownloadedSongsUseCase(
    private val repository: SongRepository,
) {
    suspend operator fun invoke(): List<SongItem> {
        try {
            return repository.getDownloadedSongs()
        } catch (e: Exception) {
            throw e.toAppError()
        }
    }
}

class LikeSongUseCase(
    private val likeService: SongLikeService,
) {
    suspend operator fun invoke(songId: String): Result<Unit> {
        return try {
            likeService.likeSong(songId)
        } catch (e: Exception) {
            Result.failure(e.toAppError())
        }
    }
}

class DislikeSongUseCase(
    private val likeService: SongLikeService,
) {
    suspend operator fun invoke(songId: String): Result<Unit> {
        return try {
            likeService.dislikeSong(songId)
        } catch (e: Exception) {
            Result.failure(e.toAppError())
        }
    }
}

class RemoveLikeSongUseCase(
    private val likeService: SongLikeService,
) {
    suspend operator fun invoke(songId: String): Result<Unit> {
        return try {
            likeService.removeLike(songId)
        } catch (e: Exception) {
            Result.failure(e.toAppError())
        }
    }
}

