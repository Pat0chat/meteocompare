package com.meteocompare.app.core.network

/**
 * Résultat d'une opération réseau / repository.
 *
 * Préféré à `kotlin.Result` car typé en covariance et expose `message`
 * sans avoir à exception-getter en deux étapes.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val exception: Throwable, val message: String) : ApiResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun onSuccess(block: (T) -> Unit): ApiResult<T> {
        if (this is Success) block(data)
        return this
    }

    inline fun onError(block: (Throwable, String) -> Unit): ApiResult<T> {
        if (this is Error) block(exception, message)
        return this
    }
}

/**
 * Wrappe un bloc suspendu dans un [ApiResult].
 * Les [CancellationException] ne sont PAS capturées (essentiel pour les coroutines).
 */
suspend inline fun <T> apiCall(crossinline block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    ApiResult.Error(e, e.toUserMessage())
}

fun Throwable.toUserMessage(): String = when (this) {
    is java.net.UnknownHostException -> "Pas de connexion internet"
    is java.net.SocketTimeoutException -> "Délai d'attente dépassé"
    is retrofit2.HttpException -> "Erreur serveur (HTTP ${code()})"
    else -> message ?: "Erreur inconnue"
}
