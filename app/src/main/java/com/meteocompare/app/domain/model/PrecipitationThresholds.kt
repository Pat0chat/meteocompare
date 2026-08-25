package com.meteocompare.app.domain.model

/**
 * Seuils d'occurrence des précipitations partagés par tout le moteur.
 *
 * Open-Meteo définit `precipitation_probability` comme la probabilité de
 * dépasser strictement 0,1 mm sur l'heure précédente. Pour que l'accord
 * déterministe (utilisé quand une PoP native manque) reste cohérent avec cette
 * définition, le moteur applique la même coupure stricte (> 0,1 mm). Sur le
 * cumul journalier, 0,1 mm sert de seuil sensible de repli quand la PoP native
 * journalière n'est pas disponible.
 *
 * Les seuils d'intensité (bruine / averses / pluie) restent distincts : ils
 * décrivent la nature du phénomène, pas son occurrence.
 */
object PrecipitationThresholds {
    const val HOURLY_OCCURRENCE_MM = 0.1
    const val DAILY_OCCURRENCE_MM = 0.1
}
