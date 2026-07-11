package com.meteocompare.app.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Une observation historique = valeur réelle mesurée pour un `targetDate`
 * dans une ville, pour une variable donnée.
 *
 * ## Clé primaire
 *
 * Composite `(cityId, variable, targetDateEpochDay)` — une seule vérité par
 * jour par variable. Un ré-enregistrement pour la même clé écrase (via
 * `OnConflictStrategy.REPLACE`) : si l'archive Open-Meteo révise ses valeurs
 * de réanalyse ERA5 rétrospectivement (rare mais possible), on met à jour
 * silencieusement.
 *
 * ## Différence de modèle vs forecast_samples
 *
 * Pas de `issuedAt` — l'observation est un fait figé (pas une prévision qui
 * évolue). `fetchedAtEpochMs` sert uniquement au debug pour tracer quand la
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
