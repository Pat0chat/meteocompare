package com.meteocompare.app.data.worker

import androidx.work.WorkInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiasHistoryRefreshStateTest {
    private fun work(state: WorkInfo.State): WorkInfo = mockk { every { this@mockk.state } returns state }

    @Test fun `attente et execution distinctes puis action disponible apres toute fin`() {
        assertEquals(BiasHistoryRefreshState.IDLE, manualBiasRefreshState(emptyList()))
        for (state in WorkInfo.State.entries) {
            val result = manualBiasRefreshState(listOf(work(state)))
            val expected = when (state) {
                WorkInfo.State.RUNNING -> BiasHistoryRefreshState.RUNNING
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> BiasHistoryRefreshState.QUEUED
                WorkInfo.State.SUCCEEDED -> BiasHistoryRefreshState.SUCCEEDED
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> BiasHistoryRefreshState.FAILED
            }
            assertEquals(expected, result)
            assertEquals(!state.isFinished, result.isActive)
        }
    }

    @Test fun `un nouveau travail actif prime sur les anciennes executions terminees`() {
        assertTrue(manualBiasRefreshState(listOf(work(WorkInfo.State.FAILED), work(WorkInfo.State.ENQUEUED))).isActive)
        assertEquals(BiasHistoryRefreshState.RUNNING,
            manualBiasRefreshState(listOf(work(WorkInfo.State.SUCCEEDED), work(WorkInfo.State.RUNNING))))
        assertFalse(BiasHistoryRefreshState.FAILED.isActive)
    }
}
