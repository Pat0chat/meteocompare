package com.meteocompare.app.data.local

import androidx.room.Entity

/**
 * Cache local des normales climatiques d'une ville.
 *
 * Clé primaire composite (cityId, month, day) : une ligne par jour-de-l'année.
 * 366 lignes par ville en théorie (avec Feb 29 si la ville est polaire et a
 * des observations significatives).
 *
 * `computedAt` est utilisé pour invalider le cache après ~6 mois (les normales
 * changent à l'échelle décennale, un refresh semestriel est très conservateur).
 *
 * `precipMeanNormal` et `windMeanNormal` sont NULLABLES pour rétro-compatibilité
 * avec un cache issu de la v2 de la DB (qui ne contenait que les températures).
 * Room ajoute automatiquement les colonnes en migration si on bump la version —
 * les anciennes lignes auront simplement NULL sur ces champs jusqu'au prochain
 * refresh (déclenché après 180 jours de staleness du cache existant).
 *
 * Note migration : cf. [MeteoCompareDatabase] pour la stratégie de version
 * bump. On préfère laisser Room recréer la table plutôt que d'écrire un
 * `ALTER TABLE ADD COLUMN` manuel — les normales se re-fetchent à la première
 * consultation d'une ville, coût acceptable, et on évite le risque de bugs
 * de migration silencieux sur un modèle domaine encore jeune.
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
    /** Vent moyen à 10m (km/h, max journalier moyenné). Nullable — cf. docblock classe. */
    val windMeanNormal: Double? = null,
    /** Epoch millis du dernier fetch agrégé. */
    val computedAt: Long
)
