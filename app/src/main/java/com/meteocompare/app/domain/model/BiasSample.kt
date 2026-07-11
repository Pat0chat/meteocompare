package com.meteocompare.app.domain.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/**
 * Un couple (prévision, observation) pour un jour cible donné — brique
 * élémentaire du calcul de biais.
 *
 * Un [BiasSample] représente : "pour la journée [targetDate], le modèle avait
 * prévu [forecast] et la réalité mesurée est [observation]". La différence
 * signée `forecast - observation` est le biais journalier ; agrégée sur ≥14
 * jours, on obtient un [ModelBias].
 *
 * @param targetDate le jour auquel la prévision faisait référence. Conservé
 *   dans le sample (plutôt qu'imposer une entrée déjà triée à l'algo) pour :
 *   (a) pouvoir dédupliquer proprement en persistance, (b) permettre une
 *   future pondération temporelle (weight = 1/age), (c) faciliter le debug.
 * @param forecast la valeur prévue par le modèle pour cette date, dans l'unité
 *   naturelle de la variable (°C, mm, km/h).
 * @param observation la valeur réellement mesurée (via archive-api Open-Meteo,
 *   qui expose des observations issues des stations et de la réanalyse ERA5).
 *
 * `@Immutable` pour rester stable Compose — les listes de [BiasSample] sont
 * passées à des composables (repo Flow → UI).
 */
@Immutable
data class BiasSample(
    val targetDate: LocalDate,
    val forecast: Double,
    val observation: Double
) {
    /** Biais journalier signé : positif = le modèle a surestimé. */
    val dailyBias: Double get() = forecast - observation
}
