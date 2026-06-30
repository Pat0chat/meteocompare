package com.meteocompare.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vérifie la connectivité réseau **avant** de lancer une requête réseau.
 *
 * Pourquoi pas attendre le timeout de Retrofit (30s par défaut) ?
 *   - L'utilisateur tape "Refresh", l'app gèle 30s, puis affiche une erreur.
 *     UX terrible. Surtout sur un refresh manuel où l'utilisateur sait
 *     parfois déjà qu'il est en mode avion.
 *   - Sur un refresh de plusieurs villes en parallèle, ça ferait 30s
 *     × N requêtes en latence avant que le `onRefreshAll` ne se termine.
 *
 * On lit `ConnectivityManager.getNetworkCapabilities()` qui retourne en quelques
 * microsecondes. Faux positif possible (le téléphone CROIT être connecté mais
 * le DNS est cassé, etc.) — auquel cas Retrofit prend le relais et timeout
 * normalement. Mais le cas commun (avion, hors couverture) est intercepté.
 *
 * Singleton parce que le ConnectivityManager est un service système : on n'a
 * pas besoin d'une instance par requête.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * `true` si le device a au moins un réseau actif avec capacité Internet.
     *
     * Note : on vérifie [NetworkCapabilities.NET_CAPABILITY_INTERNET] et
     * [NetworkCapabilities.NET_CAPABILITY_VALIDATED]. Validated = l'OS a
     * vérifié qu'on a accès au vrai internet (pas juste un portail captif
     * d'hôtel qui n'a pas été validé).
     */
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return true // Fallback permissif si pas de service
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
