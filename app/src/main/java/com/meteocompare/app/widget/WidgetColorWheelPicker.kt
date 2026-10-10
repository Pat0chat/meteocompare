package com.meteocompare.app.widget

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Action autonome de personnalisation, visuellement séparée des couleurs rapides. */
@Composable
internal fun WidgetCustomColorButton(color: Int?, labelRes: Int, tag: String, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp)
                    .background(
                        brush = color?.let { SolidColor(Color(it)) }
                            ?: Brush.sweepGradient(
                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                                    Color.Blue, Color.Magenta, Color.Red)
                            ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = color?.let {
                        formatWidgetHexColor(it)
                    } ?: stringResource(R.string.widget_color_picker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Accessible via boutons radio. Le choix n'altère aucun paramètre de refresh. */
@Composable
internal fun RowScope.WidgetCornerOption(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.weight(1f).selectable(selected = selected, role = Role.RadioButton,
            onClick = onClick).testTag(tag)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Roue teinte/saturation : centre blanc, circonférence saturée. Curseur
 * de luminosité séparé. Valeurs ARGB opaques pour préserver l'ancien
 * slider d'opacité du fond, sans introduire de double alpha.
 * Le dialogue n'écrit rien en DataStore jusqu'au Save du formulaire parent.
 */
@Composable
internal fun WidgetColorWheelDialog(
    originalArgb: Int?,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit
) {
    val initial = remember(originalArgb) {
        FloatArray(3).also { hsv ->
            AndroidColor.colorToHSV(originalArgb ?: AndroidColor.rgb(25, 118, 210), hsv)
        }
    }
    var hue by rememberSaveable(originalArgb) { mutableFloatStateOf(initial[0]) }
    var saturation by rememberSaveable(originalArgb) { mutableFloatStateOf(initial[1]) }
    var brightness by rememberSaveable(originalArgb) { mutableFloatStateOf(initial[2]) }
    // The HEX field and wheel edit the same draft. Keep exact RGB when typing:
    // HSV round-tripping would alter e.g. #123456 on a no-op Save.
    val initialColor = originalArgb ?: AndroidColor.rgb(25, 118, 210)
    var hexInput by rememberSaveable(originalArgb) { mutableStateOf(formatWidgetHexColor(initialColor)) }
    var exactHex by rememberSaveable(originalArgb) { mutableStateOf<Int?>(null) }
    var hasEdited by rememberSaveable(originalArgb) { mutableStateOf(false) }
    val hexIsValid = parseWidgetHexColor(hexInput) != null
    val selectedColor = remember(originalArgb, hasEdited, exactHex, hue, saturation, brightness) {
        when {
            !hasEdited && originalArgb != null -> originalArgb
            exactHex != null -> exactHex!!
            else -> AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
        }
    }
    val wheelColors = remember {
        (0..36).map { i -> Color(AndroidColor.HSVToColor(floatArrayOf(i * 10f, 1f, 1f))) }
    }
    val pointerRadiusDp = 10.dp

    fun chooseAt(position: Offset, bounds: Size, radiusPadding: Float) {
        hasEdited = true
        exactHex = null
        val dx = position.x - bounds.width / 2f
        val dy = position.y - bounds.height / 2f
        val radius = (min(bounds.width, bounds.height) / 2f - radiusPadding).coerceAtLeast(1f)
        hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f)
        saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
        hexInput = formatWidgetHexColor(
            AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_color_wheel_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.widget_color_wheel_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Canvas(
                    Modifier.size(210.dp).testTag(TAG_WIDGET_COLOR_WHEEL)
                        .pointerInput(Unit) {
                            detectTapGestures { point ->
                                chooseAt(point, Size(size.width.toFloat(), size.height.toFloat()),
                                    pointerRadiusDp.toPx())
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { point ->
                                    chooseAt(point, Size(size.width.toFloat(), size.height.toFloat()),
                                        pointerRadiusDp.toPx())
                                },
                                onDrag = { change, _ ->
                                    chooseAt(change.position,
                                        Size(size.width.toFloat(), size.height.toFloat()),
                                        pointerRadiusDp.toPx())
                                    change.consume()
                                }
                            )
                        }
                        .semantics {
                            contentDescription = "${(hue + 0.5f).toInt()}°, ${(saturation * 100).toInt()}%"
                        }
                ) {
                    val wheelRadius = size.minDimension / 2f - pointerRadiusDp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(Brush.sweepGradient(wheelColors, center), radius = wheelRadius, center = center)
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color.White,
                            1f to Color.Transparent,
                            center = center,
                            radius = wheelRadius
                        ), radius = wheelRadius, center = center
                    )
                    val angle = Math.toRadians(hue.toDouble())
                    val thumb = Offset(
                        center.x + cos(angle).toFloat() * wheelRadius * saturation,
                        center.y + sin(angle).toFloat() * wheelRadius * saturation
                    )
                    drawCircle(Color.Black.copy(alpha = 0.55f), radius = 10.dp.toPx(), center = thumb,
                        style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = thumb,
                        style = Stroke(2.dp.toPx()))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.widget_color_brightness),
                    style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        exactHex = null
                        hasEdited = true
                        hexInput = formatWidgetHexColor(
                            AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
                        )
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth().testTag(TAG_WIDGET_COLOR_BRIGHTNESS)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { newValue ->
                        if (newValue.length <= 7) {
                            hexInput = newValue
                            parseWidgetHexColor(newValue)?.let { argb ->
                                // Update wheel/brightness while preserving exact typed RGB.
                                exactHex = argb
                                val hsv = FloatArray(3)
                                AndroidColor.colorToHSV(argb, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                brightness = hsv[2]
                                hasEdited = true
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.widget_color_hex_label)) },
                    placeholder = { Text("#RRGGBB") },
                    supportingText = {
                        Text(stringResource(
                            if (hexIsValid) R.string.widget_color_hex_hint
                            else R.string.widget_color_hex_invalid
                        ))
                    },
                    isError = !hexIsValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                        .showWidgetKeyboardOnFocus()
                        .testTag(TAG_WIDGET_COLOR_HEX)
                )
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.size(28.dp).background(Color(selectedColor), RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(formatWidgetHexColor(selectedColor))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(selectedColor) },
                enabled = hexIsValid,
                modifier = Modifier.testTag(TAG_WIDGET_COLOR_APPLY)
            ) {
                Text(stringResource(R.string.widget_color_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

internal const val TAG_WIDGET_COLOR_HEX = "widget_color_hex"
internal const val TAG_WIDGET_COLOR_WHEEL = "widget_color_wheel"
internal const val TAG_WIDGET_COLOR_BRIGHTNESS = "widget_color_brightness"
internal const val TAG_WIDGET_COLOR_APPLY = "widget_color_apply"
