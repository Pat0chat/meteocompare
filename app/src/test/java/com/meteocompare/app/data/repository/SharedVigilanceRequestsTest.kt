package com.meteocompare.app.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedVigilanceRequestsTest {
    @Test
    fun `deux ecrans du meme departement partagent le meme fetch`() = runTest {
        val requests = SharedVigilanceRequests<Int>(
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        val gate = CompletableDeferred<Int>()
        var count = 0
        val first = async { requests.getOrStart("13|false") { count++; gate.await() } }
        val second = async { requests.getOrStart("13|false") { count++; gate.await() } }
        runCurrent()
        assertEquals(1, count)
        gate.complete(42)
        assertEquals(42, first.await())
        assertEquals(42, second.await())
    }

    @Test
    fun `l'annulation d'un ecran ne coupe pas la requete partagee`() = runTest {
        val requests = SharedVigilanceRequests<Int>(
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        val gate = CompletableDeferred<Int>()
        var count = 0
        val first = async { requests.getOrStart("13|false") { count++; gate.await() } }
        runCurrent()
        first.cancel()
        val second = async { requests.getOrStart("13|false") { count++; gate.await() } }
        runCurrent()
        assertEquals(1, count)
        assertFalse(second.isCompleted)
        gate.complete(7)
        assertEquals(7, second.await())
        assertTrue(first.isCancelled)
    }

    @Test
    fun `deux modes cotiers sont des requetes distinctes`() = runTest {
        val requests = SharedVigilanceRequests<Int>(
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        var count = 0
        val plain = async { requests.getOrStart("13|false") { ++count } }
        val coast = async { requests.getOrStart("13|true") { ++count } }
        assertEquals(1, plain.await())
        assertEquals(2, coast.await())
        assertEquals(2, count)
    }

    @Test
    fun `apres echec un nouvel appel peut repartir`() = runTest {
        val requests = SharedVigilanceRequests<Int>(
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        var count = 0
        val first = runCatching { requests.getOrStart("13|false") { count++; error("network") } }
        assertTrue(first.isFailure)
        val second = requests.getOrStart("13|false") { count++; 99 }
        assertEquals(99, second)
        assertEquals(2, count)
    }

    @Test
    fun `purge attend la requete en cours`() = runTest {
        val requests = SharedVigilanceRequests<Int>(
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        val gate = CompletableDeferred<Int>()
        val active = async { requests.getOrStart("13|false") { gate.await() } }
        runCurrent()
        val purge = async { requests.awaitInFlight(listOf("13|false", "13|true")) }
        runCurrent()
        assertFalse(purge.isCompleted)
        gate.complete(9)
        purge.await()
        assertEquals(9, active.await())
    }
}
