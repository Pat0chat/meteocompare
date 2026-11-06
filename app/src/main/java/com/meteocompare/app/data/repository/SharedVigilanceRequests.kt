package com.meteocompare.app.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Une requête distante appartient au repository, pas à la durée de vie d'un
 * écran. Un écran fermé n'annule donc pas le téléchargement attendu ailleurs.
 * Seuls les appels de même département ET de même mode côtier se rejoignent.
 */
internal class SharedVigilanceRequests<T>(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val active = mutableMapOf<String, Deferred<T>>()

    suspend fun getOrStart(key: String, request: suspend () -> T): T {
        val deferred = mutex.withLock {
            active[key]?.takeIf { !it.isCompleted } ?: run {
                val task = scope.async(start = CoroutineStart.LAZY) { request() }
                active[key] = task
                task.invokeOnCompletion {
                    scope.launch {
                        mutex.withLock {
                            if (active[key] === task) active.remove(key)
                        }
                    }
                }
                task.start()
                task
            }
        }
        // L'annulation de cet await NE CANCELLE PAS le Deferred du repository.
        return deferred.await()
    }

    /** Attend les écritures en cours avant une purge définitive du cache. */
    suspend fun awaitInFlight(keys: List<String>) {
        val tasks = mutex.withLock { keys.mapNotNull { active[it] } }
        tasks.forEach { task ->
            try {
                task.join()
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            }
        }
    }
}
