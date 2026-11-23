package com.meteocompare.app.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSingleFlightTest {
    @Test fun `concurrent widgets share an in flight request without persistent caching`() = runTest {
        val flight = WidgetSingleFlight<String, Int>()
        val unblock = CompletableDeferred<Unit>()
        var requests = 0
        val first = async { flight.getOrLoad("Paris:daily") { requests++; unblock.await(); 42 } }
        runCurrent()
        val second = async { flight.getOrLoad("Paris:daily") { requests++; 88 } }
        runCurrent()
        assertEquals(1, requests)
        unblock.complete(Unit)
        assertEquals(42, first.await())
        assertEquals(42, second.await())
        // Once complete, the request is no longer cached. Refreshes may
        // read new settings and new forecasts.
        assertEquals(43, flight.getOrLoad("Paris:daily") { requests++; 43 })
        assertEquals(2, requests)
    }


    @Test fun `cancelled owner cannot cancel a still active widget waiting for the data`() = runTest {
        val flight = WidgetSingleFlight<String, Int>()
        val blocked = CompletableDeferred<Unit>()
        var loads = 0
        val owner = async {
            flight.getOrLoad("Paris:hourly") { loads++; blocked.await(); 1 }
        }
        runCurrent()
        val waiter = async {
            flight.getOrLoad("Paris:hourly") { loads++; 2 }
        }
        runCurrent()
        assertEquals(1, loads)

        // For example, the launcher discarded the first size during resize.
        owner.cancelAndJoin()
        assertEquals(2, waiter.await())
        assertEquals(2, loads)
        // The restarted load must not become a persistent cache entry.
        assertEquals(3, flight.getOrLoad("Paris:hourly") { 3 })
    }

    @Test fun `cancelling one waiter does not interrupt the other widget load`() = runTest {
        val flight = WidgetSingleFlight<String, Int>()
        val blocked = CompletableDeferred<Unit>()
        var loads = 0
        val owner = async {
            flight.getOrLoad("Paris:daily") { loads++; blocked.await(); 42 }
        }
        runCurrent()
        val waiter = async {
            flight.getOrLoad("Paris:daily") { loads++; 99 }
        }
        runCurrent()
        waiter.cancelAndJoin()
        blocked.complete(Unit)
        assertEquals(42, owner.await())
        assertEquals(1, loads)
    }

    @Test fun `different widget content requests do not share data`() = runTest {
        val flight = WidgetSingleFlight<String, Int>()
        val one = async { flight.getOrLoad("Paris:daily") { 1 } }
        val two = async { flight.getOrLoad("Paris:insight") { 2 } }
        assertEquals(1, one.await())
        assertEquals(2, two.await())
    }

    @Test fun `a failed fetch is removed before a later refresh`() = runTest {
        val flight = WidgetSingleFlight<String, Int>()
        val failure = runCatching {
            flight.getOrLoad("Paris:daily") { error("offline") }
        }
        assertTrue(failure.isFailure)
        assertEquals(5, flight.getOrLoad("Paris:daily") { 5 })
    }
}
