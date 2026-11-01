package com.meteocompare.app.domain.model

import kotlinx.serialization.Serializable

private val LEGACY_FRANCE_COUNTRY_NAMES = setOf("France", "Frankreich", "Francia")

/**
 * Représente une ville géolocalisée.
 *
 * @property id Identifiant stable (utilisé pour le stockage des favoris).
 *              Pour les villes issues de l'API geocoding, on utilise l'id Open-Meteo.
 * @property name Nom de la ville (ex: "Paris").
 * @property admin1 Région administrative (ex: "Île-de-France"). Null si non disponible.
 * @property country Pays (ex: "France").
 * @property latitude Latitude WGS84.
 * @property longitude Longitude WGS84.
 * @property timezone Timezone IANA (ex: "Europe/Paris"). Optionnel — l'API forecast
 *           peut résoudre la timezone automatiquement avec `timezone=auto`.
 */
@Serializable
data class City(
    val id: String,
    val name: String,
    val admin1: String? = null,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null,
    /** Active les données mer / côte pour cette ville après validation côtière. */
    val marineEnabled: Boolean = false,
    /** Code pays ISO-3166-1 alpha2, ex. FR. */
    val countryCode: String? = null,
    /** Nom du département français (admin2), si disponible. */
    val departmentName: String? = null,
    /** Code de département utilisé par le Worker Vigilance, ex. 91, 2A, 971. */
    val departmentCode: String? = null,
    /** Subdivisions supplémentaires Open-Meteo, lorsqu'elles existent. */
    val admin3: String? = null,
    val admin4: String? = null,
    /** Premier code postal fourni par le géocodeur, si disponible. */
    val postalCode: String? = null
) {
    /**
     * True uniquement pour une localité française éligible à la Vigilance Météo-France.
     *
     * Les nouvelles villes disposent de [countryCode]. Les libellés historiques couvrent
     * les cinq langues actuellement supportées afin de conserver les favoris créés avant
     * l’ajout du code ISO sans effectuer de requête réseau pour les villes étrangères.
     */
    val isFrenchLocation: Boolean
        get() {
            val iso = countryCode?.trim().orEmpty()
            if (iso.isNotEmpty()) return iso.equals("FR", ignoreCase = true)
            return LEGACY_FRANCE_COUNTRY_NAMES.any { it.equals(country.trim(), ignoreCase = true) }
        }

    /** Informations distinctives, ordonnées verticalement dans les interfaces. */
    val locationDetails: List<String>
        get() = listOf(country, admin1, departmentName, admin3, admin4, postalCode)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .filterNot { it.equals(name.trim(), ignoreCase = true) }
            .distinctBy { it.lowercase(java.util.Locale.ROOT) }

    /** Deux lignes maximum pour les cartes Home : pays, puis précisions disponibles.
     * Le libellé accessible conserve toutes les subdivisions même si la seconde
     * ligne est visuellement tronquée sur un petit écran.
     */
    val homeLocationLines: List<String>
        get() = locationDetails.let { details ->
            when (details.size) {
                0, 1 -> details
                else -> listOf(details.first(), details.drop(1).joinToString(" · "))
            }
        }

    /** Libellé court pour l'UI : "Paris, Île-de-France". */
    val shortLabel: String
        get() = if (admin1 != null) "$name, $admin1" else "$name, $country"
}
