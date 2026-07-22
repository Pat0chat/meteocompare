package com.meteocompare.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vérifie et observe la connectivité réseau avant de lancer une requête.
 *
 * [isOnline] sert aux chemins one-shot (repository, refresh manuel). Le flux
 * [observeOnline] alimente l'interface en temps réel : lorsqu'un réseau validé
 * disparaît, les écrans déjà ouverts peuvent signaler qu'ils affichent les
 * données conservées dans Room au lieu de laisser croire qu'elles sont fraîches.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** `true` quand Android voit un réseau actif disposant d'un accès Internet validé. */
    fun isOnline(): Boolean {
        val manager = connectivityManager() ?: return true
        return manager.isValidatedInternetAvailable()
    }

    /**
     * Flux chaud par souscription de l'état réseau courant.
     *
     * Une première valeur est émise immédiatement, puis chaque changement de
     * réseau/capacités provoque une nouvelle lecture. [distinctUntilChanged]
     * évite les recompositions lorsque plusieurs callbacks Android décrivent le
     * même état logique.
     */
    fun observeOnline(): Flow<Boolean> = callbackFlow {
        val manager = connectivityManager()
        if (manager == null) {
            trySend(true)
            close()
            return@callbackFlow
        }

        fun publish() {
            trySend(manager.isValidatedInternetAvailable())
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish()
            override fun onLost(network: Network) = publish()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = publish()
        }

        publish()
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.registerDefaultNetworkCallback(callback)
            } else {
                manager.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback
                )
            }
        }.isSuccess

        if (!registered) {
            // L'observation temps réel est un confort UI. La valeur initiale
            // reste utilisable même si un environnement atypique refuse le callback.
            close()
        }

        awaitClose {
            if (registered) runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()

    private fun connectivityManager(): ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private fun ConnectivityManager.isValidatedInternetAvailable(): Boolean {
        val network = activeNetwork ?: return false
        val capabilities = getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
