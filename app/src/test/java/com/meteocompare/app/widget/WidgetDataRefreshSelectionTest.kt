package com.meteocompare.app.widget

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataRefreshSelectionTest {

    @Test
    fun `cache perime - attend la seconde emission fraiche`() = runTest {
        var fetchReached = false
        val result = flow {
            emit("cached")
            fetchReached = true
            emit("fresh")
        }.awaitWidgetTerminalEmission()

        assertTrue("Le flux ne doit pas etre annule apres le cache", fetchReached)
        assertEquals("fresh", result)
    }

    @Test
    fun `cache frais - retourne l unique emission`() = runTest {
        val result = flow { emit("cached") }.awaitWidgetTerminalEmission()

        assertEquals("cached", result)
    }

    @Test
    fun `flux vide - retourne null`() = runTest {
        val result = flow<String> { }.awaitWidgetTerminalEmission()

        assertEquals(null, result)
    }
}
