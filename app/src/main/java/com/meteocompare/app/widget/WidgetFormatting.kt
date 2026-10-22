package com.meteocompare.app.widget

import com.meteocompare.app.core.units.WeatherUnits

import com.meteocompare.app.domain.model.WeatherCondition

/**
 * Helpers purs de formatage pour l'affichage widget.
 *
 * Extraits de [MeteoWidget] pour :
 *   1. Rester composables sans dépendance Glance/Android → testables en JVM
 *      pur (voir WidgetFormattingTest).
 *   2. Alléger le fichier MeteoWidget qui contient déjà la composition,
 *      la sélection de layouts, les couleurs, etc.
 *
 * Marqués `internal` : accessibles depuis le package widget (MeteoWidget les
 * consomme) et depuis les tests du même package.
 */

/**
 * Construit la ligne d'extras affichée sous la ville/min-max dans les layouts
 * 4×1 et 4×2.
 *
 * Format : `"☁︎ 60% · ↝ 15 km/h · ☂︎ 1.2 mm (78%)"`
 *
 * ─── Règles d'affichage par élément ────────────────────────────────────
 * - **Cloud cover** : uniquement quand la condition est PARTLY_CLOUDY ou
 *   OVERCAST. Un ciel clair avec "☁ 15%" ajoute du bruit — le pictogramme
 *   principal (☀) porte déjà l'info. En pluie, la couverture est implicite
 *   (il y a nécessairement des nuages).
 * - **Vent** : toujours quand disponible. Contrairement aux nuages, un
 *   "vent nul" est une info utile (jour idéal pour sortir en photo,
 *   contexte pour l'humidité, etc.). Arrondi à l'entier — la précision
 *   sous-km/h n'a pas d'utilité pratique sur un widget.
 * - **Précipitations** : dès qu'un modèle prévoit >0 mm sur la journée.
 *   Le % historique nommé "confidence" représente ici la convergence entre modèles et reste le signal éditorial le
 *   plus fort de l'app — on le montre entre parenthèses quand disponible.
 *
 * Séparateur "·" (middle dot U+00B7) plutôt qu'une virgule : plus léger
 * visuellement à petite taille de police, préférence typographique.
 */
internal fun buildExtrasLine(data: WidgetData, units: WeatherUnits = WeatherUnits(data.unitSystem), includeApparent: Boolean = false, apparentPrefix: String = "Ress."): String {
    val parts = mutableListOf<String>()
    if (includeApparent) data.apparentTemp?.takeIf(Double::isFinite)?.let {
        parts += "$apparentPrefix ${units.temp(it)}"
    }

    val cond = data.currentCondition
    val showCloud = data.currentCloudCover != null &&
        (cond == WeatherCondition.PARTLY_CLOUDY || cond == WeatherCondition.OVERCAST)
    if (showCloud) {
        parts += "☁︎ ${data.currentCloudCover}%"
    }

    data.currentWindSpeedKmh?.let { wind ->
        parts += "↝ ${units.speed(wind)}" + (data.currentWindCardinal?.let { " · $it" } ?: "")
    }

    data.precipMm?.let { mm ->
        val precip = buildString {
            append("☂︎ ${units.rain(mm)}")
            data.precipConfidencePct?.let { append(" ($it%)") }
        }
        parts += precip
    }

    return parts.joinToString(" · ")
}

/**
 * Libellé concis du ressenti destiné à la ligne des températures min/max.
 * Les valeurs absentes/non finies et les formats trop étroits ne créent aucun élément Glance.
 * Le préfixe traduit est fourni par la couche UI (aucune ressource Android ici).
 */
internal fun inlineApparentTemperature(
    apparentTemp: Double?,
    units: WeatherUnits,
    prefix: String,
    show: Boolean
): String? = if (show) {
    apparentTemp?.takeIf(Double::isFinite)?.let { "$prefix ${units.temp(it)}" }
} else null

/**
 * Évite de présenter le ressenti seul dans une ligne dont les min/max sont absents.
 * Les unités et valeurs manquantes sont gérées par le helper de formatage partagé.
 */
internal fun apparentNextToMinMax(
    minMax: String,
    apparentTemp: Double?,
    units: WeatherUnits,
    prefix: String,
    show: Boolean
): String? = inlineApparentTemperature(
    apparentTemp = apparentTemp,
    units = units,
    prefix = prefix,
    show = show && minMax.isNotBlank()
)

/**
 * Compose un libellé Glance atomique : Min/Max suivi du ressenti, jamais
 * la température actuelle. Même sur un écran étroit, Glance ne peut pas
 * placer le ressenti dans une autre cellule de la Row.
 */
internal fun minMaxAndApparentText(
    minMax: String,
    apparentTemp: Double?,
    units: WeatherUnits,
    prefix: String,
    show: Boolean
): String {
    val apparent = apparentNextToMinMax(minMax, apparentTemp, units, prefix, show)
    return if (apparent != null) "$minMax · $apparent" else minMax
}

/**
 * Formate une température (Celsius) pour l'affichage widget : "22°" ou "—"
 * si la valeur est null. L'arrondi à l'entier suffit pour un widget — le
 * demi-degré n'est pas actionnable à cette échelle d'affichage.
 */
internal fun formatTemp(value: Double?, units: WeatherUnits = WeatherUnits()): String =
    units.temp(value)

/**
 * Formate min/max pour l'affichage widget : "12° / 22°", ou l'un des deux
 * seul avec préfixe si l'autre manque. Chaîne vide si les deux manquent
 * (le layout appelle isNotEmpty() avant d'afficher).
 */
internal fun formatMinMax(min: Double?, max: Double?, units: WeatherUnits = WeatherUnits()): String = when {
    min != null && max != null -> "${units.temp(min)} / ${units.temp(max)}"
    max != null -> "max ${units.temp(max)}"
    min != null -> "min ${units.temp(min)}"
    else -> ""
}
