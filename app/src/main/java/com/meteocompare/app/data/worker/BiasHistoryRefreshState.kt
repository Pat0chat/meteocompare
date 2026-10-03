package com.meteocompare.app.data.worker

import androidx.work.WorkInfo

/** État du rattrapage manuel partagé par les écrans et restauré par WorkManager. */
enum class BiasHistoryRefreshState {
    IDLE, QUEUED, RUNNING, SUCCEEDED, FAILED;

    val isActive: Boolean get() = this == QUEUED || this == RUNNING
}

internal fun manualBiasRefreshState(work: List<WorkInfo>): BiasHistoryRefreshState = when {
    work.any { it.state == WorkInfo.State.RUNNING } -> BiasHistoryRefreshState.RUNNING
    work.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } ->
        BiasHistoryRefreshState.QUEUED
    work.any { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED } ->
        BiasHistoryRefreshState.FAILED
    work.any { it.state == WorkInfo.State.SUCCEEDED } -> BiasHistoryRefreshState.SUCCEEDED
    else -> BiasHistoryRefreshState.IDLE
}
