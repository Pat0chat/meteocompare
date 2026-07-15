package com.meteocompare.app.core.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CoroutineExtTest {

    @Test
    fun `runSuspendCatching renvoie les erreurs ordinaires`() = runTest {
        val result = runSuspendCatching<Int> { error("boom") }

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `runSuspendCatching repropage les annulations`() = runTest {
        try {
            runSuspendCatching<Unit> { throw CancellationException("cancelled") }
            fail("CancellationException attendue")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }
    }
}
