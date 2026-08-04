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
 * Impact concret sur la palette (10 paliers × 3 grandeurs) :
 *   - textes NOIRS : la grande majorité des couleurs (jaunes, oranges,
 *     verts pâles, bleus clairs à moyens jusqu'à #1E88E5 L≈0.235).
 *   - textes BLANCS : uniquement les bleus foncés (#1976D2 L≈0.178, #1565C0,
 *     #0D47A1) et le rouge sombre #C62828 (L≈0.14) — les couleurs des
 *     paliers les plus extrêmes de chaque échelle.
 */
internal fun contrastingContentColor(background: Color): Color =
    if (background.luminance() > 0.179f) Color.Black else Color.White

// ─── Palette : justification des seuils ──────────────────────────────────────
//
//  Pourquoi 10 paliers discrets et non une interpolation continue :
//  10 est un compromis entre "assez de résolution pour voir un dégradé fluide"
//  et "chaque saut de couleur reste identifiable comme un signal". À 20+ bins,
//  les couleurs adjacentes se confondent et on perd l'avantage cognitif de la
//  granularité. À 5 bins (version précédente), le dégradé est trop grossier
//  pour distinguer p.ex. "pluie faible" (1 mm sur l’heure) de "pluie soutenue" (3 mm sur l’heure)
//  qui tomberaient dans le même bin.
//
//  Pourquoi une palette différente des tableaux daily :
//  daily = 4-5 bins avec des couleurs très saturées (le texte doit rester
//  distinguable des autres textes). Hourly = 10 bins avec un dégradé
//  monochromatique pour chaque grandeur (blue → dark blue pour la pluie,
//  yellow → red pour le vent, blue → red pour la température) — ce qui
//  reste lisible même en niveaux de gris (accessibilité daltonisme) et
//  évoque immédiatement l'intensité relative sans avoir à lire la légende.
//
//  Pourquoi des seuils quasi-logarithmiques pour la pluie (0.05, 0.1, 0.2,
//  0.5, 1, 2, 3, 5, 7, 10) :
//  la perception d'intensité de pluie est logarithmique — la différence
//  entre 0.1 et 0.5 mm sur l’heure est plus sensible que celle entre 8 et 10.

/**
 * Heatmap de la température horaire.
 *
 * 10 paliers absolus (indépendants des normales climatiques, pour une lecture
 * cohérente d'une ville à l'autre), pas de 5° au milieu, endpoints ouverts :
 *
 *   | Palier    | Bornes (°C)  | Couleur     | Sémantique          |
 *   |-----------|--------------|-------------|---------------------|
 *   | 1  glacial| < -10        | #0D47A1     | polaire             |
 *   | 2         | [-10, -5)    | #1565C0     | très froid          |
 *   | 3         | [-5, 0)      | #1E88E5     | froid               |
 *   | 4         | [0, 5)       | #4FC3F7     | frais               |
 *   | 5         | [5, 10)      | #B3E5FC     | frais léger         |
 *   | 6         | [10, 15)     | #DCEDC8     | doux                |
 *   | 7         | [15, 20)     | #FFF59D     | tempéré             |
 *   | 8         | [20, 25)     | #FFB74D     | chaud               |
 *   | 9         | [25, 30)     | #FF7043     | très chaud          |
 *   | 10 canicule| ≥ 30        | #C62828     | canicule            |
 *
 * Contrairement à la version "styler texte" qui aurait pu laisser la zone
 * tempérée en null, ici *tous* les paliers ont une couleur — c'est le principe
 * même d'une heatmap : chaque cellule participe à la carte thermique. Les
 * paliers "doux" (10-15°) et "tempéré" (15-20°) reçoivent des couleurs claires
 * (vert pâle, jaune pâle) qui restent distinctes du fond de rangée mais ne
 * "polluent" pas visuellement — elles constituent le canevas sur lequel les
 * extrêmes ressortent.
 *
 * Progression bleu foncé → bleu clair → vert pâle → jaune → orange → rouge :
 * gradient "cold-to-warm" universellement reconnaissable. Le passage
 * bleu-clair → vert pâle → jaune (paliers 5-6-7) constitue la zone "confortable"
 * qui bascule visuellement entre "il fait froid" et "il fait chaud".
 */
internal fun hourlyTemperatureHeatmap(celsius: Double): HeatmapCellStyle = when {
    celsius >= 30.0 -> HeatmapCellStyle(background = Color(0xFFC62828)) // rouge — canicule
    celsius >= 25.0 -> HeatmapCellStyle(background = Color(0xFFFF7043)) // deep orange — très chaud
    celsius >= 20.0 -> HeatmapCellStyle(background = Color(0xFFFFB74D)) // orange clair — chaud
    celsius >= 15.0 -> HeatmapCellStyle(background = Color(0xFFFFF59D)) // jaune pâle — tempéré
    celsius >= 10.0 -> HeatmapCellStyle(background = Color(0xFFDCEDC8)) // vert pâle — doux
    celsius >= 5.0  -> HeatmapCellStyle(background = Color(0xFFB3E5FC)) // bleu très pâle — frais léger
    celsius >= 0.0  -> HeatmapCellStyle(background = Color(0xFF4FC3F7)) // bleu clair — frais
    celsius >= -5.0 -> HeatmapCellStyle(background = Color(0xFF1E88E5)) // bleu — froid
    celsius >= -10.0 -> HeatmapCellStyle(background = Color(0xFF1565C0)) // bleu foncé — très froid
    else            -> HeatmapCellStyle(background = Color(0xFF0D47A1)) // bleu profond — polaire
}

/**
 * Heatmap des précipitations horaires en mm sur l’heure.
 *
 * 10 paliers colorés + 1 palier neutre (retour null pour le sec) :
 *
 *   | Palier | Bornes (mm sur l’heure) | Couleur   | Sémantique              |
 *   |--------|---------------|-----------|-------------------------|
 *   | (null) | < 0.05        | —         | sec (cellule neutre)    |
 *   | 1      | [0.05, 0.1)   | #E3F2FD   | bruine à peine visible  |
 *   | 2      | [0.1, 0.2)    | #BBDEFB   | bruine légère           |
 *   | 3      | [0.2, 0.5)    | #90CAF9   | bruine                  |
 *   | 4      | [0.5, 1)      | #64B5F6   | pluie faible            |
 *   | 5      | [1, 2)        | #42A5F5   | pluie modérée           |
 *   | 6      | [2, 3)        | #2196F3   | pluie soutenue          |
 *   | 7      | [3, 5)        | #1E88E5   | pluie forte             |
 *   | 8      | [5, 7)        | #1976D2   | averse marquée          |
 *   | 9      | [7, 10)       | #1565C0   | averse violente         |
 *   | 10     | ≥ 10          | #0D47A1   | déluge / orage          |
 *
 * Pourquoi retourner null pour "sec" plutôt qu'une couleur neutre uniforme :
 * la majorité des heures d'une journée moyenne sont sèches. Si on colorait
 * ces cellules même faiblement (10ème teinte de bleu), la "densité" visuelle
 * de la heatmap masquerait le signal utile (les rares heures pluvieuses). En
 * laissant les cellules sèches transparentes, elles se fondent dans
 * l'alternance de rangée du tableau — les cellules colorées sautent aux yeux.
 *
 * Palette dégradée monochromatique (Blue 50 → Blue 900 de Material Design) :
 * chaque cellule "plus pluvieuse" est visuellement plus foncée que la
 * précédente, ce qui produit un dégradé lisible même en niveaux de gris
 * (accessibilité daltonisme). Les seuils suivent une progression
 * quasi-logarithmique (0.05, 0.1, 0.2, 0.5, 1, 2, 3, 5, 7, 10) car la
 * perception "il pleut plus" est logarithmique — la différence entre 0.1 et
 * 0.5 mm sur l’heure est plus sensible que celle entre 8 et 10.
 */
internal fun hourlyPrecipitationHeatmap(mm: Double): HeatmapCellStyle? = when {
    mm < 0.05 -> null
    mm < 0.1  -> HeatmapCellStyle(background = Color(0xFFE3F2FD)) // Blue 50
    mm < 0.2  -> HeatmapCellStyle(background = Color(0xFFBBDEFB)) // Blue 100
    mm < 0.5  -> HeatmapCellStyle(background = Color(0xFF90CAF9)) // Blue 200
    mm < 1.0  -> HeatmapCellStyle(background = Color(0xFF64B5F6)) // Blue 300
    mm < 2.0  -> HeatmapCellStyle(background = Color(0xFF42A5F5)) // Blue 400
    mm < 3.0  -> HeatmapCellStyle(background = Color(0xFF2196F3)) // Blue 500
    mm < 5.0  -> HeatmapCellStyle(background = Color(0xFF1E88E5)) // Blue 600
    mm < 7.0  -> HeatmapCellStyle(background = Color(0xFF1976D2)) // Blue 700
    mm < 10.0 -> HeatmapCellStyle(background = Color(0xFF1565C0)) // Blue 800
    else      -> HeatmapCellStyle(background = Color(0xFF0D47A1)) // Blue 900
}

/**
 * Heatmap du vent instantané en km/h.
 *
 * 10 paliers colorés + 1 palier neutre (retour null pour le calme) :
 *
 *   | Palier | Bornes (km/h) | Couleur   | Sémantique (~Beaufort)  |
 *   |--------|---------------|-----------|-------------------------|
 *   | (null) | < 20          | —         | calme (cellule neutre)  |
 *   | 1      | [20, 30)      | #FFF9C4   | brise légère (B3)       |
 *   | 2      | [30, 40)      | #FFF176   | brise (B4)              |
 *   | 3      | [40, 50)      | #FFEB3B   | brise soutenue (B5)     |
 *   | 4      | [50, 60)      | #FFCA28   | brise fraîche (B6)      |
 *   | 5      | [60, 70)      | #FFB74D   | vent frais (B7 début)   |
 *   | 6      | [70, 80)      | #FF9800   | grand frais (B8)        |
 *   | 7      | [80, 90)      | #FB8C00   | coup de vent (B9)       |
 *   | 8      | [90, 100)     | #F57C00   | fort coup de vent (B10) |
 *   | 9      | [100, 120)    | #E64A19   | tempête (B11)           |
 *   | 10     | ≥ 120         | #C62828   | ouragan (B12)           |
 *
 * Même logique de "null pour le calme" que pour les précipitations : ne pas
 * saturer la vue de couleur là où l'info est "rien d'inhabituel". Les vents
 * remarquables ressortent d'autant mieux.
 *
 * Progression jaune → orange → rouge cohérente avec les codes de vigilance
 * Météo-France, et distincte du bleu des précipitations pour éviter la
 * confusion visuelle entre les deux tables adjacentes. Les seuils sont calés
 * sur l'échelle de Beaufort (paliers de 10 km/h dans la zone "vent utilisable"
 * 20-100, élargis à 20 km/h au-delà où les distinctions perceptives
 * s'estompent — un cyclone à 130 km/h et à 140 km/h se ressemblent).
 */
internal fun hourlyWindHeatmap(kmh: Double): HeatmapCellStyle? = when {
    kmh < 20.0  -> null
    kmh < 30.0  -> HeatmapCellStyle(background = Color(0xFFFFF9C4)) // Yellow 100
    kmh < 40.0  -> HeatmapCellStyle(background = Color(0xFFFFF176)) // Yellow 300
    kmh < 50.0  -> HeatmapCellStyle(background = Color(0xFFFFEB3B)) // Yellow 500
    kmh < 60.0  -> HeatmapCellStyle(background = Color(0xFFFFCA28)) // Amber 400
    kmh < 70.0  -> HeatmapCellStyle(background = Color(0xFFFFB74D)) // Orange 300
    kmh < 80.0  -> HeatmapCellStyle(background = Color(0xFFFF9800)) // Orange 500
    kmh < 90.0  -> HeatmapCellStyle(background = Color(0xFFFB8C00)) // Orange 600
    kmh < 100.0 -> HeatmapCellStyle(background = Color(0xFFF57C00)) // Orange 700
    kmh < 120.0 -> HeatmapCellStyle(background = Color(0xFFE64A19)) // Deep Orange 700
    else        -> HeatmapCellStyle(background = Color(0xFFC62828)) // Red 800 — ouragan
}