package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Réponse de `/v1/forecast?models=a,b,c` — MULTI-modèles en un seul appel HTTP.
 *
 * ─── Différence avec [ForecastResponseDto] ─────────────────────────────
 * Quand plusieurs modèles sont demandés, Open-Meteo suffixe CHAQUE variable
 * par la clé du modèle correspondant, tout en gardant un axe des temps unifié
 * dans le fuseau demandé (`timezone=auto` en production), partagé par les modèles.
 *
 * Exemple pour `?models=arome_france_hd,arpege_europe` :
 * ```json
 * {
 *   "hourly": {
 *     "time": [...],                                  // partagé
 *     "temperature_2m_arome_france_hd": [...],        // suffixé
 *     "temperature_2m_arpege_europe":  [...],
 *     "precipitation_arome_france_hd": [...],
 *     "precipitation_arpege_europe":   [...],
 *     ...
 *   }
 * }
 * ```
 *
 * ─── Pourquoi JsonObject plutôt qu'un DTO typé ? ─────────────────────
 * Le SET des clés dépend des modèles demandés en runtime (K variables ×
 * N modèles = jusqu'à ~70 clés dynamiques). Un @Serializable typé impose
 * une liste fermée à la compilation, incompatible avec cet usage. On garde
 * donc `hourly`/`daily` en [JsonObject] et on les décompose côté domaine
 * dans [BatchedForecastSplitter], qui reconstruit des [ForecastResponseDto]
 * unitaires (compatibles avec le mapper et le format de cache existants —
 * pas de refactor invasif).
 *
 * ─── Corner case : mode single-modèle ─────────────────────────────────
 * Si le splitter reçoit un seul modèle dans requestedModels, les variables
 * ne sont PAS suffixées côté Open-Meteo (backward compat historique de leur
 * API). Le splitter détecte ce cas et fallback sur les clés non-suffixées.
 * Signifie qu'on peut utiliser ce DTO même pour un modèle unique — le
 * pipeline batched devient l'unique code path.
 *
 * ─── Corner case : premier modèle hors couverture ─────────────────────
 * `latitude`/`longitude` décrivent la maille du PREMIER modèle de la liste
 * `models=`. Quand ce modèle ne couvre pas le point (ex. AROME HD pour une
 * ville d'Asie ou d'Afrique), Open-Meteo renvoie `"latitude": null,
 * "longitude": null` tout en fournissant normalement les séries des autres
 * modèles. Ces champs sont donc nullables : obligatoires, ils faisaient
 * échouer la désérialisation de toute la réponse (« Erreur inconnue »).
 * Aucune logique métier ne dépend de ces coordonnées de maille.
 */
@Serializable
data class BatchedForecastResponseDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String,
    /** JSON brut de la section `hourly` — clés dynamiques suffixées par modèle. */
    val hourly: JsonObject? = null,
    /** JSON brut de la section `daily` — même logique de suffixage. */
    val daily: JsonObject? = null
)
