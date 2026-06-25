package com.meteocompare.app.domain.model

import kotlinx.serialization.Serializable

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
    val timezone: String? = null
) {
    /** Libellé court pour l'UI : "Paris, Île-de-France". */
    val shortLabel: String
        get() = if (admin1 != null) "$name, $admin1" else "$name, $country"
}
