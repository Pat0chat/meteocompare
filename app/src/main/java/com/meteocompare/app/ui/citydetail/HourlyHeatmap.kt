package com.meteocompare.app.ui.citydetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Style d'une cellule en mode heatmap : la valeur n'est plus signalée par la
 * couleur du TEXTE (comme le fait [ValueStyle]) mais par la couleur de FOND
 * de la cellule. Le texte reprend automatiquement une couleur qui contraste
 * avec le fond pour rester lisible.
 *
 * Différence de responsabilité vs [ValueStyle] :
 *   - [ValueStyle] : styling *typographique* — colore les valeurs remarquables
 *     tout en gardant un fond neutre (rangées alternées). Utilisé par les
 *     tableaux daily où on a 7 lignes seulement et où le scan visuel se fait
 *     ligne par ligne.
 *   - [HeatmapCellStyle] : styling *chromatique* de la cellule entière —
 *     transforme la matrice en carte thermique. Utilisé par les tableaux
 *     hourly où on a jusqu'à 24 lignes × 5 colonnes = 120 cellules, et où
 *     l'œil doit repérer les zones "chaudes" / "froides" au premier coup
 *     d'œil sans lire chaque valeur.
 *
 * @param background couleur de fond de la cellule.
 * @param contentColor couleur du texte à utiliser sur ce fond. Par défaut
 *   calculée automatiquement via [contrastingContentColor] pour garantir la
 *   lisibilité (WCAG 4.5:1 approché via seuil de luminance).
 */
data class HeatmapCellStyle(
    val background: Color,
    val contentColor: Color = contrastingContentColor(background)
)

/**
 * Renvoie la couleur de texte (noir ou blanc) qui contraste le mieux avec
 * le fond fourni.
 *
 * Seuil à 0.179 sur la luminance relative — dérivé du calcul WCAG :
 *   contraste = (L_clair + 0.05) / (L_sombre + 0.05)
 * Au point L = 0.179 les deux ratios (noir-sur-fond et blanc-sur-fond) sont
 * égaux, chacun à ~4.58 (au-dessus du seuil AA de 4.5). Choisir 0.179 comme
 * bascule maximise donc le contraste dans le pire cas.
 *
 * Divergence assumée vs le reste de l'app : `OpacityPreview` et
 * `HourlyConfidenceChart` utilisent 0.5. Ces contextes détectent un "thème
 * clair vs sombre" pour un rendu esthétique ; ici on choisit du texte lisible
 * sur des couleurs de heatmap denses (jusqu'à 120 cellules à scanner). Le
 * problème est différent — d'où le seuil différent.
 *
 * Impact concret sur la palette : le corail #FF7043 (L≈0.33), la rouge
 * canicule #E53935 (L≈0.20) et le bleu clair #4FC3F7 (L≈0.47) prennent du
 * texte NOIR (bien plus lisible que blanc) ; les bleus foncés #1565C0 /
 * #0D47A1 et le rouge tempête #C62828 prennent du texte BLANC.
 */
internal fun contrastingContentColor(background: Color): Color =
    if (background.luminance() > 0.179f) Color.Black else Color.White

// ─── Palette : justification des seuils ──────────────────────────────────────
//
//  Pourquoi des paliers discrets et non une interpolation continue :
//  identique à [precipitationStyle]/[windStyle] daily — l'œil identifie mieux
//  un saut de couleur "propre" qu'un dégradé bruité pixel par pixel. 4-5 bins
//  calés sur des seuils meteo réels rendent chaque palier interprétable.
//
//  Pourquoi coller à la palette existante des tableaux daily :
//  cohérence visuelle. Un utilisateur qui bascule daily → hourly reconnaît
//  "bleu foncé = pluie forte", "rouge = tempête", etc. La seule différence
//  est que la couleur est maintenant sur le FOND et non plus sur le texte.

/**
 * Heatmap de la température horaire.
 *
 * 5 paliers absolus (indépendants des normales climatiques, pour une lecture
 * cohérente d'une ville à l'autre) :
 *   - < 0°   : bleu foncé  (gel)
 *   - 0-5°   : bleu clair  (frais)
 *   - 5-20°  : vert pâle   (tempéré — bin qui était NULL dans le styler texte)
 *   - 20-30° : corail      (chaud)
 *   - ≥ 30°  : rouge       (canicule)
 *
 * Contrairement à la version "styler texte" qui aurait pu laisser la zone
 * tempérée en null, ici *tous* les paliers ont une couleur — c'est le principe
 * même d'une heatmap : chaque cellule participe à la carte thermique. La zone
 * tempérée reçoit une couleur douce (vert pâle) qui reste distincte du fond
 * mais ne "pollue" pas visuellement — elle constitue le canevas sur lequel
 * les extrêmes ressortent.
 *
 * Les seuils sont figés en constantes intra-fonction plutôt que partagés :
 * volontaire, pour que la légende (dans CityDetailScreen) et cette fonction
 * puissent évoluer indépendamment si le design change les tranches sans que
 * l'autre bin ne se retrouve à traduire des seuils périmés.
 */
internal fun hourlyTemperatureHeatmap(celsius: Double): HeatmapCellStyle = when {
    celsius >= 30.0 -> HeatmapCellStyle(background = Color(0xFFE53935)) // rouge
    celsius >= 20.0 -> HeatmapCellStyle(background = Color(0xFFFF7043)) // corail
    celsius > 5.0   -> HeatmapCellStyle(background = Color(0xFFDCEDC8)) // vert pâle
    celsius >= 0.0  -> HeatmapCellStyle(background = Color(0xFF4FC3F7)) // bleu clair
    else            -> HeatmapCellStyle(background = Color(0xFF1E88E5)) // bleu foncé
}

/**
 * Heatmap des précipitations horaires en mm/h.
 *
 * 4 paliers colorés + 1 palier neutre (retour null) :
 *   - < 0.05 mm/h : null      (sec — pas de signal → cellule neutre)
 *   - 0.05-0.5    : bleu très clair  (bruine)
 *   - 0.5-2       : bleu clair       (pluie modérée)
 *   - 2-5         : bleu              (pluie forte)
 *   - > 5         : bleu très foncé  (orage)
 *
 * Pourquoi retourner null pour "sec" plutôt qu'une couleur neutre uniforme :
 * la majorité des heures d'une journée moyenne sont sèches. Si on colorait
 * ces cellules même faiblement, la "densité" visuelle de la heatmap
 * masquerait le signal utile (les rares heures pluvieuses). En laissant les
 * cellules sèches transparentes, elles se fondent dans l'alternance de rangée
 * du tableau — les cellules colorées sautent aux yeux.
 *
 * Les seuils sont calés sur des paliers meteo réels (bruine / modéré / fort /
 * orage) et reprennent ceux utilisés par le styler texte historique pour que
 * la légende reste unique — comprise à l'identique entre modes hourly/daily.
 */
internal fun hourlyPrecipitationHeatmap(mm: Double): HeatmapCellStyle? = when {
    mm < 0.05 -> null
    mm < 0.5  -> HeatmapCellStyle(background = Color(0xFF4FC3F7))
    mm < 2.0  -> HeatmapCellStyle(background = Color(0xFF1E88E5))
    mm < 5.0  -> HeatmapCellStyle(background = Color(0xFF1565C0))
    else      -> HeatmapCellStyle(background = Color(0xFF0D47A1))
}

/**
 * Heatmap du vent instantané en km/h.
 *
 * 4 paliers colorés + 1 palier neutre :
 *   - < 20 km/h : null       (calme — pas de signal)
 *   - 20-40     : orange clair  (brise)
 *   - 40-60     : orange        (vent modéré)
 *   - 60-80     : orange foncé  (vent fort)
 *   - > 80      : rouge foncé   (tempête)
 *
 * Même logique de "null pour le calme" que pour les précipitations : ne pas
 * saturer la vue de couleur là où l'info est "rien d'inhabituel". Les vents
 * remarquables ressortent d'autant mieux.
 *
 * Progression orange → rouge cohérente avec les codes de vigilance
 * Météo-France, et distincte du bleu des précipitations pour éviter la
 * confusion visuelle entre les deux tables adjacentes.
 */
internal fun hourlyWindHeatmap(kmh: Double): HeatmapCellStyle? = when {
    kmh < 20.0 -> null
    kmh < 40.0 -> HeatmapCellStyle(background = Color(0xFFFFB74D))
    kmh < 60.0 -> HeatmapCellStyle(background = Color(0xFFFB8C00))
    kmh < 80.0 -> HeatmapCellStyle(background = Color(0xFFE64A19))
    else       -> HeatmapCellStyle(background = Color(0xFFC62828))
}
