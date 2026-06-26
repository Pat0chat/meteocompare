package com.meteocompare.app.domain.model

/**
 * Préférence de thème de l'utilisateur.
 *
 * SYSTEM suit le mode sombre/clair défini par l'OS — choix par défaut.
 * LIGHT et DARK forcent l'app dans un mode indépendamment du système, utile
 * pour les gens qui aiment leur OS en sombre mais l'app météo en clair (ou
 * vice-versa).
 */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        /** Conversion sûre depuis la chaîne DataStore. Inconnu → SYSTEM. */
        fun fromString(value: String?): ThemePreference = when (value) {
            "LIGHT" -> LIGHT
            "DARK" -> DARK
            else -> SYSTEM
        }
    }
}
