package com.example.melodist.domain.home

import com.metrolist.innertube.pages.HomePage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class HomeUseCasesTest {

    private class FakeHomeRemote : HomeRemoteDataSource {
        var lastParams: String? = null
        var lastContinuation: String? = null

        override suspend fun getHome(params: String?, continuation: String?): Result<HomePage> {
            lastParams = params
            lastContinuation = continuation
            return Result.success(
                HomePage(
                    chips = emptyList(),
                    sections = emptyList(),
                    continuation = continuation,
                )
            )
        }
    }

    @Test
    fun loadHomeUseCase_delegatesToRemoteDataSource() = runBlocking {
        val remote = FakeHomeRemote()
        val useCase = LoadHomeUseCase(remote)

        val result = useCase(params = "FEtrending").getOrThrow()

        assertEquals("FEtrending", remote.lastParams)
        assertTrue(result.sections.isEmpty())
        assertEquals(null, result.continuation)
    }

    @Test
    fun loadHomeUseCase_supportsContinuation() = runBlocking {
        val remote = FakeHomeRemote()
        val useCase = LoadHomeUseCase(remote)

        val result = useCase(continuation = "next-home-token").getOrThrow()

        assertEquals("next-home-token", remote.lastContinuation)
        assertEquals("next-home-token", result.continuation)
    }
}

