package com.meteocompare.app.domain.model

/**
 * Ordre d'affichage commun des modèles dans les vues de comparaison.
 *
 * Les modèles d'une même institution sont regroupés, puis classés de la
 * résolution la plus fine à la plus large. L'ordinal reste le dernier critère
 * afin de garantir un ordre déterministe lorsque deux modèles ont la même
 * résolution déclarée.
 */
val WeatherModelFamilyComparator: Comparator<WeatherModel> =
    compareBy<WeatherModel>(
        { it.family.ordinal },
        { it.resolutionKm },
        { it.ordinal }
    )

/** Retourne les modèles regroupés par famille dans un ordre stable. */
fun Iterable<WeatherModel>.sortedByFamily(): List<WeatherModel> =
    sortedWith(WeatherModelFamilyComparator)
