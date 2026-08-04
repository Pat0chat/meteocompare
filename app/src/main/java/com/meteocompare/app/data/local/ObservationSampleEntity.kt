package com.meteocompare.app.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Une référence météorologique historique pour un `targetDate`, une ville
 * et une variable. La table conserve son nom historique `observation_samples`
 * pour compatibilité de schéma, mais la valeur provient de l'archive
 * Open-Meteo et n'est pas une mesure de station au point exact.
 *
 * ## Clé primaire
 *
 * Composite `(cityId, variable, targetDateEpochDay)` — une seule vérité par
 * jour par variable. Un ré-enregistrement pour la même clé écrase (via
 * `OnConflictStrategy.REPLACE`) : si la référence historique Open-Meteo est révisée
 * rétrospectivement, la valeur locale est mise à jour.
 *
 * ## Différence de modèle vs forecast_samples
 *
 * Pas de `issuedAt` — la référence est associée au jour validé, pas à un run
 * de prévision. `fetchedAtEpochMs` sert uniquement au debug pour tracer quand la
 * ligne a été insérée en cache, jamais pour la logique métier.
 *
 * ## Index
 *
 * Sur `targetDateEpochDay` pour les purges. La clé composite gère déjà
 * l'accès par (city, variable) qui est le pattern de lookup normal.
 */
@Entity(
    tableName = "observation_samples",
    primaryKeys = ["cityId", "variable", "targetDateEpochDay"],
    indices = [Index(value = ["targetDateEpochDay"])]
)
data class ObservationSampleEntity(
    val cityId: String,
    val variable: String,
    val targetDateEpochDay: Long,
    val value: Double,
    /** Epoch millis du fetch archive qui a inséré cette ligne (debug seulement). */
    val fetchedAtEpochMs: Long
)
