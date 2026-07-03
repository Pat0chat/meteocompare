package com.meteocompare.app.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.meteocompare.app.R
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * Palier temporel utilisé pour formater "il y a X".
 *
 * Sealed class + data classes plutôt qu'un enum : la valeur numérique (nombre
 * de minutes/heures/jours) doit voyager AVEC le palier, sinon on doit
 * dupliquer le calcul dans le formatage. Comme sous-produit, ça rend
 * [computeLastUpdatedPalier] complètement testable sans Context — un test
 * peut asserter `assertEquals(Minutes(5), computeLastUpdatedPalier(...))` sans
 * mocker quoi que ce soit.
 *
 * `internal` : ce type est un détail d'implémentation, pas de l'API publique
 * de l'UI. Seul le test dans le même module doit y avoir accès.
 */
internal sealed class LastUpdatedPalier {
    /** < 1 min — l'utilisateur voit "à l'instant". */
    data object JustNow : LastUpdatedPalier()
    /** 1..59 min. */
    data class Minutes(val value: Int) : LastUpdatedPalier()
    /** 1..23 h. */
    data class Hours(val value: Int) : LastUpdatedPalier()
    /** ≥ 1 jour. */
    data class Days(val value: Int) : LastUpdatedPalier()
}

/**
 * Choix du palier depuis [fetchedAt] jusqu'à [now]. Pure — pas de Context, pas
 * d'i18n, uniquement l'arithmétique.
 *
 * Règles d'échelle — choisies pour équilibrer précision et compacité :
 *
 *   - < 60 s   → [LastUpdatedPalier.JustNow]  (le "il y a 30 s" n'apporte rien
 *                                              de plus visuellement)
 *   - < 60 min → [LastUpdatedPalier.Minutes]  (précision minute — l'échelle
 *                                              intuitive pour un check récent)
 *   - < 24 h   → [LastUpdatedPalier.Hours]    (précision heure — "il y a 62
 *                                              min" est bruité vs "il y a 1h")
 *   - sinon    → [LastUpdatedPalier.Days]     (précision jour — au-delà d'une
 *                                              journée l'info devient "vieux")
 *
 * La troncation entière (`/ 60`, `/ 3600`, `/ 86_400`) est délibérée : elle
 * évite les arrondis trompeurs. Il vaut mieux dire "il y a 3h" à 3h 55 min que
 * "il y a 4h" alors que l'utilisateur pourrait s'attendre à un rafraîchissement
 * automatique dans 5 min.
 */
internal fun computeLastUpdatedPalier(
    fetchedAt: Instant,
    now: Instant
): LastUpdatedPalier {
    // Duration.between peut être négatif si l'horloge a reculé entre le fetch
    // et maintenant (NTP correctif, changement manuel de l'heure système).
    // On coerce à 0 pour ne pas afficher "il y a -3 min" absurde à l'utilisateur.
    val secondsAgo = Duration.between(fetchedAt, now).seconds.coerceAtLeast(0L)

    return when {
        secondsAgo < 60L -> LastUpdatedPalier.JustNow
        secondsAgo < 3600L -> LastUpdatedPalier.Minutes((secondsAgo / 60L).toInt())
        secondsAgo < 86_400L -> LastUpdatedPalier.Hours((secondsAgo / 3600L).toInt())
        else -> LastUpdatedPalier.Days((secondsAgo / 86_400L).toInt())
    }
}

/**
 * Intervalle avant le prochain re-tick d'affichage (en ms), en fonction du
 * palier courant. Extrait en fonction pure pour être testable et pour éviter
 * de tourner en boucle serrée quand on est dans un palier stable (heure/jour).
 *
 *   - JustNow (< 1 min)  → 15 s : bascule rapidement vers "il y a 1 min"
 *   - Minutes (< 1 h)    → 30 s : rafraîchit à la mi-minute pour rester précis
 *   - Hours ou Days      → 5 min : ces paliers changent rarement, inutile de
 *                                  faire tourner un LaunchedEffect toutes les
 *                                  30 s pour rien
 */
internal fun refreshIntervalMsFor(palier: LastUpdatedPalier): Long = when (palier) {
    LastUpdatedPalier.JustNow -> 15_000L
    is LastUpdatedPalier.Minutes -> 30_000L
    is LastUpdatedPalier.Hours -> 300_000L
    is LastUpdatedPalier.Days -> 300_000L
}

/**
 * Formate un [Instant] en caption "mis à jour il y a X" localisé via [context].
 *
 * Cette fonction est un thin wrapper autour de [computeLastUpdatedPalier] : elle
 * mappe chaque palier à sa string ressource localisée. La logique métier
 * (choix du palier + arrondi) est déléguée au pur — la seule chose testée ici
 * en pratique serait "chaque palier passe la bonne resId à getString", ce que
 * la revue de code couvre déjà.
 *
 * @param context requis pour accéder aux [R.string] (résolution locale).
 * @param fetchedAt instant à formater.
 * @param now instant "maintenant" (injectable pour tests, défaut = Instant.now()).
 */
fun formatLastUpdated(
    context: Context,
    fetchedAt: Instant,
    now: Instant = Instant.now()
): String = when (val palier = computeLastUpdatedPalier(fetchedAt, now)) {
    LastUpdatedPalier.JustNow -> context.getString(R.string.updated_just_now)
    is LastUpdatedPalier.Minutes -> context.getString(R.string.updated_min_ago, palier.value)
    is LastUpdatedPalier.Hours -> context.getString(R.string.updated_hour_ago, palier.value)
    is LastUpdatedPalier.Days -> context.getString(R.string.updated_day_ago, palier.value)
}

/**
 * Version @Composable qui rafraîchit automatiquement le libellé au fil du temps.
 *
 * Sans ce refresh, un utilisateur qui laisserait l'écran ouvert 30 minutes
 * verrait toujours "à l'instant" — mensonger. Un [LaunchedEffect] re-tick à
 * la fréquence adaptée au palier courant (voir [refreshIntervalMsFor]).
 *
 * Rendu comme un simple [String] utilisable dans un Text() :
 * ```
 * Text(text = rememberFormattedLastUpdated(fetchedAt))
 * ```
 */
@Composable
fun rememberFormattedLastUpdated(fetchedAt: Instant): String {
    val context = LocalContext.current
    // On re-key sur fetchedAt : si les données sont rafraîchies, on redémarre
    // le compteur avec la nouvelle valeur "à l'instant".
    var label by remember(fetchedAt) {
        mutableStateOf(formatLastUpdated(context, fetchedAt))
    }

    // Boucle de refresh périodique. LaunchedEffect(fetchedAt) : le job est
    // annulé et relancé quand fetchedAt change (nouveau refresh manuel), ce
    // qui remet immédiatement l'affichage sur "à l'instant".
    LaunchedEffect(fetchedAt) {
        while (true) {
            val palier = computeLastUpdatedPalier(fetchedAt, Instant.now())
            delay(refreshIntervalMsFor(palier))
            label = formatLastUpdated(context, fetchedAt)
        }
    }

    return label
}
