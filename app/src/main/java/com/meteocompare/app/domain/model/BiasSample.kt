package com.meteocompare.app.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate

/**
 * Un couple (prévision, référence historique) pour un jour cible donné — brique
 * élémentaire du calcul de biais.
 *
 * Un [BiasSample] représente : "pour la journée [targetDate], le modèle avait
 * prévu [forecast] et la référence historique vaut [observation]". La différence
 * signée `forecast - observation` est le biais journalier ; agrégée sur ≥14
 * jours, on obtient un [ModelBias].
 *
 * @param targetDate le jour auquel la prévision faisait référence. Conservé
 *   dans le sample (plutôt qu'imposer une entrée déjà triée à l'algo) pour :
 *   (a) pouvoir dédupliquer proprement en persistance, (b) permettre une
 *   future pondération temporelle (weight = 1/age), (c) faciliter le debug.
 * @param forecast la valeur prévue par le modèle pour cette date, dans l'unité
 *   naturelle de la variable (°C, mm, km/h).
 * @param observation la valeur historique de référence fournie par l'archive
 *   Open-Meteo. Elle provient de jeux de données de réanalyse et ne doit pas
 *   être présentée comme une mesure de station exacte au point demandé.
 * @param issuedAt marqueur de la journée locale d'émission de la prévision
 *   Previous Runs. Le collecteur le normalise au début de la veille locale
 *   pour que les rafraîchissements successifs remplacent la même ligne J+1. `null`
 *   reste accepté pour les tests et données synthétiques.
 *
 * `@Immutable` pour rester stable Compose — les listes de [BiasSample] sont
 * passées à des composables (repo Flow → UI).
 */
@Immutable
data class BiasSample(
    val targetDate: LocalDate,
    val forecast: Double,
    val observation: Double,
    val issuedAt: Instant? = null,
    /** Échéance de la prévision historique. Anciennes données = J+1. */
    val leadDay: Int = 1
) {
    /** Biais journalier signé : positif = le modèle a surestimé. */
    val dailyBias: Double get() = forecast - observation
}
