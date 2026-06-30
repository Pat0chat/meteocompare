package com.meteocompare.app.core.network

import android.content.Context
import com.meteocompare.app.R

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
 * Wrappe un bloc suspendu dans un [ApiResult] avec message d'erreur localisé.
 * Les [CancellationException] ne sont PAS capturées (essentiel pour les coroutines).
 *
 * On prend Context en paramètre pour résoudre les R.string.* dans la locale
 * courante. Sans ça, les messages d'erreur restaient en français même quand
 * l'utilisateur avait sélectionné l'anglais.
 */
suspend inline fun <T> apiCall(
    context: Context,
    crossinline block: suspend () -> T
): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    ApiResult.Error(e, e.toUserMessage(context))
}

/**
 * Convertit une exception réseau en message utilisateur localisé.
 *
 * Avant on hardcodait du français ; maintenant on lit les string resources
 * pour matcher la langue courante (FR/EN selon la sélection user).
 */
fun Throwable.toUserMessage(context: Context): String = when (this) {
    is java.net.UnknownHostException -> context.getString(R.string.error_no_network)
    is java.net.SocketTimeoutException -> context.getString(R.string.error_timeout)
    is retrofit2.HttpException -> context.getString(R.string.error_server, code())
    else -> message ?: context.getString(R.string.error_unknown)
}
