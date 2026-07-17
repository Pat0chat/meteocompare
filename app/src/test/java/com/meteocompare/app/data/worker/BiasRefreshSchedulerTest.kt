package com.meteocompare.app.data.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BiasRefreshSchedulerTest {

    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        workManager = mockk(relaxed = true)
    }

    @Test
    fun `schedule conserve le periodic existant avec KEEP`() {
        val policy = slot<ExistingPeriodicWorkPolicy>()
        every {
            workManager.enqueueUniquePeriodicWork(any(), capture(policy), any())
        } returns mockk(relaxed = true)

        BiasRefreshScheduler.schedule(workManager)

        assertEquals(ExistingPeriodicWorkPolicy.KEEP, policy.captured)
    }

    @Test
    fun `updateAfterAppReplacement migre le periodic avec UPDATE`() {
        val policy = slot<ExistingPeriodicWorkPolicy>()
        every {
            workManager.enqueueUniquePeriodicWork(any(), capture(policy), any())
        } returns mockk(relaxed = true)

        BiasRefreshScheduler.updateAfterAppReplacement(workManager)

        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, policy.captured)
    }

    @Test
    fun `le kickoff reste unique avec KEEP dans les deux chemins`() {
        BiasRefreshScheduler.schedule(workManager)
        BiasRefreshScheduler.updateAfterAppReplacement(workManager)

        verify(exactly = 2) {
            workManager.enqueueUniqueWork(any(), ExistingWorkPolicy.KEEP, any())
        }
    }
}
