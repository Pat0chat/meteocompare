package com.meteocompare.app.domain.model

/**
 * Préférence de langue de l'application.
 *
 * SYSTEM : suit la langue de l'OS — choix par défaut, comportement attendu
 *          pour la majorité des utilisateurs.
 * FRENCH / ENGLISH : force la langue indépendamment de l'OS, utile pour
 *          tester ou pour les utilisateurs francophones avec un téléphone
 *          en anglais (ou inversement).
 *
 * Mappage vers la chaîne BCP-47 utilisée par
 * `AppCompatDelegate.setApplicationLocales`. `null` = laisser au système.
 */
enum class LanguagePreference(val bcp47Tag: String?) {
    SYSTEM(null),
    FRENCH("fr"),
    ENGLISH("en");

    companion object {
        fun fromString(value: String?): LanguagePreference = when (value) {
            "FRENCH" -> FRENCH
            "ENGLISH" -> ENGLISH
            else -> SYSTEM
        }
    }
}
