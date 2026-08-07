package com.meteocompare.app.data.local

import androidx.room.Entity

/**
 * Cache local des repères calendaires ERA5 sur 10 ans d'une ville.
 *
 * Clé primaire composite (cityId, month, day) : une ligne par jour-de-l'année.
 * Jusqu'à 366 lignes par ville (29 février inclus lorsqu'il existe dans la
 * fenêtre historique et que la paire thermique est exploitable).
 *
 * `computedAt` invalide le cache après ~6 mois. La série reste un repère
 * produit sur une fenêtre glissante de 10 années complètes, pas une normale WMO.
 *
 * `precipMeanNormal` et `windMeanNormal` sont NULLABLES pour tolérer un cache
 * ancien ou une réponse d'archive partielle. Room ne modifie jamais un schéma
 * automatiquement sans migration/auto-migration déclarée : la stratégie de
 * versionnement effective est documentée dans [MeteoCompareDatabase].
 */
@Entity(
    tableName = "climate_normals",
    primaryKeys = ["cityId", "month", "day"]
)
data class ClimateNormalEntity(
    val cityId: String,
    val month: Int,
    val day: Int,
    val tempMaxNormal: Double,
    val tempMinNormal: Double,
    /** Précipitation moyenne journalière (mm/jour). Nullable — cf. docblock classe. */
    val precipMeanNormal: Double? = null,
    /** Moyenne calendaire du maximum journalier du vent à 10 m (km/h). Nullable — cf. docblock classe. */
    val windMeanNormal: Double? = null,
    /** Epoch millis du dernier fetch agrégé. */
    val computedAt: Long
)
