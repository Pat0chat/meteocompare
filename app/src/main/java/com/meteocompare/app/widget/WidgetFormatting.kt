package com.meteocompare.app.widget

import com.meteocompare.app.domain.model.WeatherCondition
import kotlin.math.roundToInt

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
 * Format : `"☁ 60% · 💨 15 km/h · 🌧 1.2 mm (78%)"`
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
 *   Le % de confiance (accord entre modèles) est le signal éditorial le
 *   plus fort de l'app — on le montre entre parenthèses quand disponible.
 *
 * Séparateur "·" (middle dot U+00B7) plutôt qu'une virgule : plus léger
 * visuellement à petite taille de police, préférence typographique.
 */
internal fun buildExtrasLine(data: WidgetData): String {
    val parts = mutableListOf<String>()

    val cond = data.currentCondition
    val showCloud = data.currentCloudCover != null &&
        (cond == WeatherCondition.PARTLY_CLOUDY || cond == WeatherCondition.OVERCAST)
    if (showCloud) {
        parts += "☁ ${data.currentCloudCover}%"
    }

    data.currentWindSpeedKmh?.let { wind ->
        parts += "💨 ${wind.roundToInt()} km/h"
    }

    data.precipMm?.let { mm ->
        val precip = buildString {
            append("🌧 %.1f mm".format(mm))
            data.precipConfidencePct?.let { append(" ($it%)") }
        }
        parts += precip
    }

    return parts.joinToString(" · ")
}

/**
 * Formate une température (Celsius) pour l'affichage widget : "22°" ou "—"
 * si la valeur est null. L'arrondi à l'entier suffit pour un widget — le
 * demi-degré n'est pas actionnable à cette échelle d'affichage.
 */
internal fun formatTemp(value: Double?): String =
    if (value == null) "—" else "${value.roundToInt()}°"

/**
 * Formate min/max pour l'affichage widget : "12° / 22°", ou l'un des deux
 * seul avec préfixe si l'autre manque. Chaîne vide si les deux manquent
 * (le layout appelle isNotEmpty() avant d'afficher).
 */
internal fun formatMinMax(min: Double?, max: Double?): String = when {
    min != null && max != null -> "${min.roundToInt()}° / ${max.roundToInt()}°"
    max != null -> "max ${max.roundToInt()}°"
    min != null -> "min ${min.roundToInt()}°"
    else -> ""
}
