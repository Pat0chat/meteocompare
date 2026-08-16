package com.meteocompare.app.domain.model

/**
 * Préférence de langue de l'application.
 *
 * SYSTEM : suit la langue de l'OS — choix par défaut.
 * Les autres entrées forcent la langue indépendamment de l'OS.
 *
 * [bcp47Tag] est la représentation stockée dans la source canonique de locale.
 * `null` signifie "suivre le système".
 */
enum class LanguagePreference(val bcp47Tag: String?) {
    SYSTEM(null),
    FRENCH("fr"),
    ENGLISH("en"),
    SPANISH("es"),
    GERMAN("de"),
    ITALIAN("it");

    companion object {
        fun fromLanguageTag(value: String?): LanguagePreference = when (value
            ?.substringBefore('-')
            ?.lowercase()) {
            "fr" -> FRENCH
            "en" -> ENGLISH
            "es" -> SPANISH
            "de" -> GERMAN
            "it" -> ITALIAN
            else -> SYSTEM
        }
    }
}
