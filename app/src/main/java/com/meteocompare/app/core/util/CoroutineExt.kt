package com.meteocompare.app.core.util

import kotlinx.coroutines.CancellationException

/**
 * Variante suspendue de [runCatching] qui respecte l'annulation structurée.
 *
 * Le `runCatching` standard capture aussi [CancellationException]. Dans un
 * worker ou un repository, cela peut transformer une annulation normale en
 * succès partiel ou en retry et laisser du travail continuer inutilement.
 */
suspend inline fun <T> runSuspendCatching(
    crossinline block: suspend () -> T
): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    Result.failure(error)
}
