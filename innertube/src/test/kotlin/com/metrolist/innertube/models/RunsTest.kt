package com.metrolist.innertube.models

import org.junit.Test
import org.junit.Assert.assertEquals

class RunsTest {
    @Test
    fun `splits comma and Spanish y artist separators`() {
        val result = listOf(Run("Artist A, Artist B y Artist C", null)).splitArtistRuns()

        assertEquals(
            listOf("Artist A", "Artist B", "Artist C"),
            result.map { it.text },
        )
    }

    @Test
    fun `does not keep standalone comma from China artist byline`() {
        val result = listOf(
            Run("Ozuna", null),
            Run(", ", null),
            Run("J Balvin", null),
        ).splitArtistRuns()

        assertEquals(listOf("Ozuna", "J Balvin"), result.map { it.text })
    }
}
