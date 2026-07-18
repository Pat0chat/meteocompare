package com.meteocompare.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.meteocompare.app.domain.model.WeatherModel

/**
 * Palette chromatique des modèles météo.
 *
 * Chaque institution utilise une teinte de base unique. Ses différents modèles
 * sont distingués par des nuances plus ou moins claires de cette même teinte :
 *
 *   - Météo-France : bleus
 *   - DWD : oranges
 *   - NOAA : violets
 *   - ECMWF : ambres
 *
 * Les institutions qui ne proposent qu'un modèle conservent une couleur propre.
 * Les valeurs restent volontairement assez contrastées pour être lisibles sur
 * les thèmes clair et sombre, y compris dans les courbes superposées.
 */
private val ModelColorMap: Map<WeatherModel, Color> = mapOf(
    // Météo-France — une même famille de bleus, du modèle le plus fin au global.
    WeatherModel.AROME_FRANCE_HD to Color(0xFF0D47A1),
    WeatherModel.AROME_FRANCE to Color(0xFF1565C0),
    WeatherModel.ARPEGE_EUROPE to Color(0xFF1976D2),
    WeatherModel.ARPEGE_WORLD to Color(0xFF42A5F5),

    // DWD — déclinaisons orange, de la haute résolution au modèle global.
    WeatherModel.ICON_D2 to Color(0xFFBF360C),
    WeatherModel.ICON_EU to Color(0xFFE64A19),
    WeatherModel.ICON_GLOBAL to Color(0xFFFF7043),

    // NOAA — violets : HRRR plus sombre, GFS plus lumineux.
    WeatherModel.HRRR_CONUS to Color(0xFF4A148C),
    WeatherModel.GFS to Color(0xFF7B1FA2),

    // ECMWF — ambres : IFS plus profond, AIFS plus lumineux.
    WeatherModel.ECMWF to Color(0xFFF57F17),
    WeatherModel.ECMWF_AIFS to Color(0xFFFBC02D),

    // Familles à modèle unique — teintes distinctes entre institutions.
    WeatherModel.UKMO_GLOBAL to Color(0xFF00838F),
    WeatherModel.GEM_GLOBAL to Color(0xFFC2185B),
    WeatherModel.METNO_NORDIC to Color(0xFF00695C),
    WeatherModel.KNMI_HARMONIE_EU to Color(0xFF795548),
    WeatherModel.BOM_ACCESS to Color(0xFF283593),
    WeatherModel.CMA_GRAPES to Color(0xFFAD1457)
)

/**
 * Couleur du modèle. Le gris signale visuellement qu'un nouveau modèle n'a pas
 * encore reçu de couleur explicite.
 */
fun WeatherModel.color(): Color = ModelColorMap[this] ?: Color(0xFF9E9E9E)

// Helpers de validation utilisés par les tests unitaires.
internal fun modelsWithoutExplicitColor(): List<WeatherModel> =
    WeatherModel.entries.filter { it !in ModelColorMap }

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
