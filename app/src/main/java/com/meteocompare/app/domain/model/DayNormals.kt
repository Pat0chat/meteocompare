package com.meteocompare.app.domain.model

/**
 * Normale climatique pour un jour de l'année (indépendant de l'année).
 *
 * Calculée par agrégation de plusieurs années (typiquement 10) de données
 * historiques pour le même day-of-year, avec lissage glissant pour éviter
 * les sauts irrégaliers entre jours consécutifs.
 *
 * Note : "normale" au sens strict = moyenne 30 ans (norme OMM), mais 10 ans
 * donnent une approximation lisible visuellement avec une bande passante
 * réduite côté réseau. À documenter dans l'UI ("Référence climatique 10 ans").
 */
data class DayNormals(
    val month: Int,    // 1-12
    val day: Int,      // 1-31
    val tempMaxNormal: Double,
    val tempMinNormal: Double
) {
    companion object {
        /** Clé compacte pour lookup O(1) par (month, day). */
        fun key(month: Int, day: Int): Int = month * 100 + day
    }

    val key: Int get() = key(month, day)
}
