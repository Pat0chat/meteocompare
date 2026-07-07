package com.meteocompare.app.domain.model

/**
 * Normale climatique pour un jour de l'année (indépendant de l'année).
 *
 * Calculée par agrégation de plusieurs années (typiquement 10) de données
 * historiques pour le même day-of-year. Sert de repère "à quoi ressemble
 * habituellement cette date" — les graphes de confiance affichent ces
 * valeurs en pointillés pour permettre de repérer d'un coup d'œil un
 * jour anormalement chaud/pluvieux/venteux.
 *
 * Note : "normale" au sens strict = moyenne 30 ans (norme OMM), mais 10 ans
 * donnent une approximation lisible visuellement avec une bande passante
 * réduite côté réseau. À documenter dans l'UI ("Référence climatique 10 ans").
 *
 * Les champs précipitation et vent sont NULLABLES car ils sont arrivés dans
 * une version postérieure de l'app. Un cache Room qui contient uniquement les
 * températures reste utilisable — l'UI omet simplement le trait pointillé
 * correspondant sur les graphes concernés.
 */
data class DayNormals(
    val month: Int,    // 1-12
    val day: Int,      // 1-31
    val tempMaxNormal: Double,
    val tempMinNormal: Double,
    /**
     * Cumul de précipitations journalier moyen (mm/jour), moyenné sur les
     * ~10 dernières années pour ce day-of-year. Nullable :
     *   - Cache issu d'une version antérieure à cette feature.
     *   - Aucune observation exploitable (rare, mais possible en zone polaire).
     */
    val precipMeanNormal: Double? = null,
    /**
     * Vitesse moyenne du vent à 10m (km/h) — moyenne journalière, moyennée sur
     * les ~10 dernières années. On prend la moyenne journalière plutôt que le
     * max horaire pour rester représentatif du "vent typique de cette date" et
     * pas de la seule pointe.
     */
    val windMeanNormal: Double? = null
) {
    companion object {
        /** Clé compacte pour lookup O(1) par (month, day). */
        fun key(month: Int, day: Int): Int = month * 100 + day
    }

    val key: Int get() = key(month, day)
}
