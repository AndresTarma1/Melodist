@file:Suppress("unused")

package com.example.melodist.domain.home

import com.example.melodist.domain.error.toAppError
import com.metrolist.innertube.pages.HomePage

interface HomeRemoteDataSource {
    suspend fun getHome(params: String? = null, continuation: String? = null): Result<HomePage>
}

class LoadHomeUseCase(
    private val remoteDataSource: HomeRemoteDataSource,
) {
    suspend operator fun invoke(params: String? = null, continuation: String? = null): Result<HomePage> {
        return remoteDataSource.getHome(params = params, continuation = continuation)
            .mapCatching { it }
            .recoverCatching { throw it.toAppError() }
    }
}


