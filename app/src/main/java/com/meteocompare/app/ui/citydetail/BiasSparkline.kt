package com.meteocompare.app.ui.citydetail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.BiasDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sparkline 30 jours prévision vs observation, avec l'écart matérialisé en
 * enveloppe colorée. Idiome standard des "coefficient bias plots" en
 * météorologie / trading — l'utilisateur *voit* le motif systématique du
 * biais avant même de lire les stats numériques.
 *
 * Trois couches empilées :
 *   1. **Enveloppe** — polygon fermé entre les deux courbes, rempli semi-
 *      transparent dans la couleur de direction. Grande = biais visuel fort.
 *   2. **Observation** — ligne pointillée en teinte neutre (`onSurfaceVariant`).
 *      La référence : "ce qui s'est passé pour de vrai".
 *   3. **Prévision** — ligne pleine dans la couleur de direction (matche le
 *      chip qui a été tapé). Le jugement du modèle sur la même période.
 *
 * L'ordre importe : enveloppe d'abord (arrière), obs ensuite, forecast au-
 * dessus (met en avant "le modèle raconte cette histoire").
 *
 * ## Décisions produit verrouillées
 *
 *   - **Agrégation quotidienne** — 30 points, un par jour (pas horaire).
 *   - **Axe Y fixé** — bornes par variable, calculées côté caller sur l'union
 *     de tous les modèles + observation. Permet de comparer visuellement
 *     l'ampleur du biais entre modèles en tapant successivement chaque chip.
 *   - **Axe Y nu, seulement deux labels X** ("J−30" et "Aujourd'hui") — un
 *     sparkline "vrai", pas un mini-graphe : la précision numérique est dans
 *     la grille de stats juste dessous.
 *   - **Interaction statique** — pas de scrub. Ajouté en Phase 1.6 dédiée si
 *     besoin s'en fait sentir.
 *
 * ## Animation
 *
 * Séquencée en 3 phases (~1,2 s au total, respecte les usages Material) :
 *   1. `0 → 500ms` : la ligne d'observation se dessine gauche → droite
 *   2. `300 → 800ms` : la ligne de prévision se dessine (chevauche fin phase 1)
 *   3. `700 → 1100ms` : l'enveloppe apparaît en fade
 *
 * Le chevauchement des phases 1-2 crée la sensation "la réalité passe, puis
 * le modèle passe" ; l'enveloppe qui apparaît en dernier concrétise
 * visuellement l'écart entre les deux — c'est LE moment "aha" de la sheet.
 *
 * Passer `animate = false` pour désactiver (tests, preview, ou pour un
 * futur support de `prefers-reduced-motion` piloté par le caller).
 *
 * @param forecast 30 valeurs chronologiques (index 0 = J−29, index 29 = aujourd'hui).
 * @param observation 30 valeurs chronologiques dans la même unité que [forecast].
 *   Doit avoir la même taille que [forecast].
 * @param direction sens du biais — pilote la couleur de la ligne prévision et
 *   de l'enveloppe.
 * @param yDomainMin borne inférieure de l'axe Y, en unité de la variable.
 * @param yDomainMax borne supérieure — doit être strictement > [yDomainMin].
 * @param animate `true` pour rejouer la séquence d'animation à chaque
 *   composition (défaut).
 */
@Composable
internal fun BiasSparkline(
    forecast: List<Double>,
    observation: List<Double>,
    direction: BiasDirection,
    yDomainMin: Double,
    yDomainMax: Double,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    require(forecast.size == observation.size) {
        "forecast and observation must have same size (got ${forecast.size} vs ${observation.size})"
    }
    require(forecast.size >= 2) {
        "sparkline needs at least 2 points, got ${forecast.size}"
    }
    require(yDomainMax > yDomainMin) {
        "yDomainMax ($yDomainMax) must be > yDomainMin ($yDomainMin)"
    }

    val palette = biasChipPalette(direction, pending = false)
    val forecastColor = palette.foreground
    val envelopeColor = palette.foreground
    val observationColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Surface tonale légère, alignée sur les autres panneaux de la page.
    // La couleur du biais reste un accent et ne devient jamais le fond
    // dominant, ce qui garde le graphe lisible en clair comme en sombre.
    val backgroundTint = palette.foreground
        .copy(alpha = 0.055f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    val panelShape = RoundedCornerShape(14.dp)

    // ── État d'animation : 3 progressions indépendantes ──
    // Chacun remonte de 0 → 1 (obs et fcst = fraction de path visible via
    // PathMeasure ; envelope = alpha du fill).
    val obsProgress = remember { Animatable(if (animate) 0f else 1f) }
    val fcstProgress = remember { Animatable(if (animate) 0f else 1f) }
    val envelopeAlpha = remember { Animatable(if (animate) 0f else ENVELOPE_ALPHA) }

    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        // Séquence chevauchante — voir doc de la fonction.
        launch {
            obsProgress.animateTo(1f, tween(durationMillis = 500, easing = EaseOutCubic))
        }
        delay(300)
        launch {
            fcstProgress.animateTo(1f, tween(durationMillis = 500, easing = EaseOutCubic))
        }
        delay(400)
        launch {
            envelopeAlpha.animateTo(ENVELOPE_ALPHA, tween(durationMillis = 400))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(panelShape)
            .background(backgroundTint)
            .border(
                width = 1.dp,
                color = palette.foreground.copy(alpha = 0.14f),
                shape = panelShape
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // ── Canvas des courbes ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val paths = buildSparklinePaths(
                    forecast = forecast,
                    observation = observation,
                    yDomainMin = yDomainMin,
                    yDomainMax = yDomainMax,
                    canvasSize = size
                )

                // 1. Enveloppe (arrière) — fade in via alpha uniquement
                drawPath(
                    path = paths.envelope,
                    color = envelopeColor.copy(alpha = envelopeAlpha.value)
                )

                // 2. Observation (milieu) — dashed, révélée gauche → droite
                //    via PathMeasure.getSegment. Le pathEffect dashed est
                //    appliqué APRÈS le clip de segment, donc les tirets
                //    apparaissent au bon endroit sur la portion visible.
                val obsDash = PathEffect.dashPathEffect(
                    floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                )
                val obsRevealed = clipPathToFraction(paths.observation, obsProgress.value)
                drawPath(
                    path = obsRevealed,
                    color = observationColor,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = obsDash
                    )
                )

                // 3. Prévision (avant) — solide, révélée gauche → droite.
                //    Dessinée par-dessus l'observation pour être l'élément
                //    "principal" du regard (le modèle raconte cette histoire).
                val fcstRevealed = clipPathToFraction(paths.forecast, fcstProgress.value)
                drawPath(
                    path = fcstRevealed,
                    color = forecastColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Labels X aux extrémités uniquement ──
        // Décision produit : sparkline nu, pas de mini-graphe. Juste "d'où on
        // vient" et "où on est maintenant" — la valeur exacte à J−17 n'est
        // pas utile ici (elle est déjà consolidée dans les stats).
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.bias_sparkline_label_past, forecast.size - 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.bias_sparkline_label_today),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── Mini-légende observation / prévision ──
        SparklineLegend(
            observationColor = observationColor,
            forecastColor = forecastColor
        )
    }
}

/**
 * Mini-légende deux éléments : swatch stylisé (pointillé/plein) + label.
 * Placée en pied du sparkline pour lever l'ambiguïté "quelle ligne est
 * laquelle" sans nécessiter une légende encombrante — deux mentions courtes.
 */
@Composable
private fun SparklineLegend(
    observationColor: Color,
    forecastColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        LegendEntry(
            color = observationColor,
            dashed = true,
            label = stringResource(R.string.bias_sparkline_legend_observation)
        )
        LegendEntry(
            color = forecastColor,
            dashed = false,
            label = stringResource(R.string.bias_sparkline_legend_forecast)
        )
    }
}

@Composable
private fun LegendEntry(color: Color, dashed: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Swatch : petit rectangle de 18dp de large qui reproduit exactement
        // le style de trait utilisé dans le canvas (pointillé pour obs,
        // continu pour forecast).
        Canvas(modifier = Modifier.size(width = 18.dp, height = 2.dp)) {
            val effect = if (dashed) {
                PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
            } else null
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = if (dashed) 1.5.dp.toPx() else 2.dp.toPx(),
                pathEffect = effect,
                cap = StrokeCap.Round
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Géométrie pure — testable en JVM sans compose runtime
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Alpha final de l'enveloppe une fois l'animation terminée. Constante extraite
 * pour être référencée en test (vérification "state final = 0.28").
 */
internal const val ENVELOPE_ALPHA: Float = 0.28f

/**
 * Bundle des trois paths qui composent le sparkline. Séparés parce que
 * chacun a un style de rendu différent (fill vs stroke, dashed vs solid).
 */
internal data class SparklinePaths(
    val observation: Path,
    val forecast: Path,
    val envelope: Path
)

/**
 * Calcule les points d'affichage d'une série numérique projetée dans un
 * repère canvas (origine en haut à gauche, y croissant vers le bas).
 *
 * Fonction pure : entrée data + repère → sortie coordonnées. Cœur testable.
 *
 * Cas dégénérés :
 *   - `values.size == 1` → un seul point à x=0. Le sparkline ne devrait pas
 *     l'appeler (précondition size >= 2 dans BiasSparkline), mais la fonction
 *     reste robuste.
 *   - `yDomainMin == yDomainMax` → division par zéro évitée en centrant tous
 *     les points sur la ligne du milieu (fallback discret plutôt qu'un crash).
 */
internal fun sparklinePoints(
    values: List<Double>,
    yDomainMin: Double,
    yDomainMax: Double,
    canvasSize: Size
): List<Offset> {
    if (values.isEmpty()) return emptyList()

    val w = canvasSize.width
    val h = canvasSize.height
    val yRange = yDomainMax - yDomainMin

    return values.mapIndexed { i, v ->
        val x = if (values.size == 1) 0f else i.toFloat() / (values.size - 1) * w
        val yNorm = if (yRange <= 0.0) 0.5f
                    else ((v - yDomainMin) / yRange).toFloat().coerceIn(0f, 1f)
        // Inversion : yNorm=0 (data min) doit être en BAS du canvas → y=h.
        //             yNorm=1 (data max) doit être en HAUT → y=0.
        val y = h - yNorm * h
        Offset(x, y)
    }
}

/**
 * Construit les trois paths à partir des deux séries. L'enveloppe est un
 * polygon fermé : obs forward + forecast reverse + close, dans le sens
 * horaire — remplissable directement en drawPath.
 */
internal fun buildSparklinePaths(
    forecast: List<Double>,
    observation: List<Double>,
    yDomainMin: Double,
    yDomainMax: Double,
    canvasSize: Size
): SparklinePaths {
    val obsPts = sparklinePoints(observation, yDomainMin, yDomainMax, canvasSize)
    val fcstPts = sparklinePoints(forecast, yDomainMin, yDomainMax, canvasSize)

    val obsPath = polyline(obsPts)
    val fcstPath = polyline(fcstPts)
    val envelope = polygonClosed(envelopeVertices(obsPts, fcstPts))

    return SparklinePaths(
        observation = obsPath,
        forecast = fcstPath,
        envelope = envelope
    )
}

/**
 * Sommets du polygon d'enveloppe dans l'ordre de tracé : observation en avant
 * puis forecast à rebours. Extrait comme pure fonction pour être testable
 * sans passer par le `Path` de compose-ui (qui nécessite le runtime Android).
 *
 * @return séquence des sommets. `Path.close()` s'appliquera implicitement au
 *   moment de la construction du path graphique — cette fonction n'ajoute pas
 *   de sommet de fermeture explicite.
 */
internal fun envelopeVertices(
    observation: List<Offset>,
    forecast: List<Offset>
): List<Offset> {
    if (observation.isEmpty() || forecast.isEmpty()) return emptyList()
    return observation + forecast.reversed()
}

private fun polyline(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        lineTo(points[i].x, points[i].y)
    }
}

private fun polygonClosed(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        lineTo(points[i].x, points[i].y)
    }
    close()
}

/**
 * Renvoie un nouveau Path correspondant à la première [fraction] du path
 * d'origine (utilisé pour l'animation "line draws left-to-right").
 *
 * Utilise [PathMeasure] — mesure la longueur totale puis extrait un segment.
 * Cas dégénéré `fraction <= 0` renvoie un path vide (évite un warning
 * PathMeasure "cannot get segment of zero length").
 */
private fun clipPathToFraction(path: Path, fraction: Float): Path {
    if (fraction <= 0f) return Path()
    if (fraction >= 1f) return path

    val measure = PathMeasure()
    measure.setPath(path, forceClosed = false)
    val length = measure.length
    if (length <= 0f) return Path()

    val dest = Path()
    measure.getSegment(
        startDistance = 0f,
        stopDistance = length * fraction,
        destination = dest,
        startWithMoveTo = true
    )
    return dest
}
