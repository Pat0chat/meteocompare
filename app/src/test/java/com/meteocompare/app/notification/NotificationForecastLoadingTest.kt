package com.meteocompare.app.notification

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationForecastLoadingTest {
    private val cached = CityForecast(
        City("paris", "Paris", country = "France", latitude = 48.85, longitude = 2.35),
        emptyMap(), fetchedAt = Instant.parse("2026-10-02T06:00:00Z")
    )

    @Test fun `cache conserve si le refresh expire`() = runTest {
        val actual = latestNotificationForecast(flow {
            emit(ApiResult.Success(cached))
            awaitCancellation()
        }, timeoutMs = 1_000)
        assertEquals(cached, actual)
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test fun `cache remplace par le succes plus recent`() = runTest {
        val fresh = cached.copy(fetchedAt = cached.fetchedAt!!.plusSeconds(3_600))
        val actual = latestNotificationForecast(flow {
            emit(ApiResult.Success(cached))
            delay(100)
            emit(ApiResult.Success(fresh))
        }, timeoutMs = 1_000)
        assertEquals(fresh, actual)
    }

    @Test fun `cache conserve si le flux echoue apres son emission`() = runTest {
        assertEquals(cached, latestNotificationForecast(flow {
            emit(ApiResult.Success(cached))
            throw IOException("offline")
        }))
    }

    @Test fun `sans cache un timeout ne fabrique pas de prevision`() = runTest {
        assertNull(latestNotificationForecast(flow { awaitCancellation() }, timeoutMs = 1_000))
        assertNull(latestNotificationForecast(flowOf(ApiResult.Error(IOException(), "offline"))))
    }

    @Test fun `annulation externe propagee meme avec du cache`() = runTest {
        val task = async {
            latestNotificationForecast(flow {
                emit(ApiResult.Success(cached))
                awaitCancellation()
            })
        }
        runCurrent()
        task.cancel()
        try {
            task.await()
            throw AssertionError("An external cancellation must not return cached data")
        } catch (_: CancellationException) {
            assertTrue(task.isCancelled)
        }
    }
}
