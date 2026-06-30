package com.meteocompare.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.meteocompare.app.domain.model.WeatherModel

/**
 * Couleurs assignées explicitement à chaque modèle.
 *
 * Pourquoi un Map plutôt qu'une List<Color> indexée par ordinal :
 *   - L'approche `palette[ordinal % size]` fonctionne tant qu'on a autant
 *     d'entrées que de modèles, mais cycle silencieusement si on ajoute un
 *     modèle sans étendre la palette → deux modèles partagent la même couleur,
 *     bug invisible jusqu'à ce qu'on les superpose dans un chart.
 *   - L'approche Map force à choisir une couleur EXPLICITE à chaque ajout de
 *     modèle, le compilateur ne nous rappellera pas mais l'absence de couleur
 *     remonte un fallback visible (gris) plutôt qu'un doublon silencieux.
 *
 * Familles chromatiques pensées pour le scan visuel :
 *   - Météo-France AROME (fine résolution France) : bleus
 *   - Météo-France ARPEGE (échelle synoptique) : verts
 *   - DWD ICON (Allemagne) : oranges / rouges chauds
 *   - NOAA GFS : violet (signature unique pour le modèle US le plus connu)
 *   - ECMWF (physique et IA) : jaunes ambrés
 *   - UK Met Office : cyan / teal (couleur traditionnellement associée au UK)
 *   - Environnement Canada GEM : crimson (couleur du drapeau canadien)
 *
 * Toutes les couleurs sont calibrées pour rester lisibles sur surface clair
 * ET sombre — saturation modérée, luminance dans la zone visible des deux
 * thèmes (testé sur les charts de comparaison de températures).
 */
private val ModelColorMap: Map<WeatherModel, Color> = mapOf(
    // Météo-France AROME — bleus
    WeatherModel.AROME_FRANCE_HD to Color(0xFF1565C0), // bleu foncé
    WeatherModel.AROME_FRANCE to Color(0xFF42A5F5),    // bleu clair

    // Météo-France ARPEGE — verts
    WeatherModel.ARPEGE_EUROPE to Color(0xFF2E7D32),   // vert foncé
    WeatherModel.ARPEGE_WORLD to Color(0xFF66BB6A),    // vert clair

    // DWD ICON — oranges / rouges chauds
    WeatherModel.ICON_EU to Color(0xFFD84315),         // orange foncé
    WeatherModel.ICON_GLOBAL to Color(0xFFFF7043),     // orange clair
    WeatherModel.ICON_D2 to Color(0xFFBF360C),         // orange brûlé (variante haute-res)

    // NOAA — violet
    WeatherModel.GFS to Color(0xFF6A1B9A),

    // ECMWF — jaunes ambrés (physique foncé, IA clair)
    WeatherModel.ECMWF to Color(0xFFF9A825),           // jaune ambré
    WeatherModel.ECMWF_AIFS to Color(0xFFFFCA28),      // jaune clair (variante IA)

    // UK Met Office — cyan / teal
    WeatherModel.UKMO_GLOBAL to Color(0xFF00838F),

    // Environnement Canada — crimson
    WeatherModel.GEM_GLOBAL to Color(0xFFC2185B)
)

/**
 * Couleur du modèle. Fallback gris neutre si un nouveau modèle a été ajouté
 * à l'enum sans entrée dans la map — au moins l'UI ne crashe pas, et le gris
 * "anormal" signale visuellement qu'une correction est requise.
 */
fun WeatherModel.color(): Color = ModelColorMap[this] ?: Color(0xFF9E9E9E)

// ─── Helpers de validation (utilisés par les tests unitaires) ──────────────
// `internal` : visible depuis le module mais pas part de l'API publique.

/** Modèles présents dans l'enum sans entrée explicite dans la map. */
internal fun modelsWithoutExplicitColor(): List<WeatherModel> =
    WeatherModel.entries.filter { it !in ModelColorMap }

/**
 * Paires de modèles qui partagent EXACTEMENT la même couleur — utile pour
 * détecter un copier-coller raté. Sur les charts superposés, deux modèles
 * partageant une couleur deviennent indiscernables.
 */
internal fun duplicateModelColors(): List<Pair<WeatherModel, WeatherModel>> {
    val entries = WeatherModel.entries.toList()
    val out = mutableListOf<Pair<WeatherModel, WeatherModel>>()
    for (i in entries.indices) {
        for (j in i + 1 until entries.size) {
            if (entries[i].color() == entries[j].color()) {
                out += entries[i] to entries[j]
            }
        }
    }
    return out
}
