package com.meteocompare.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Adapt the JVM-testable sizing catalog to Glance without Android grid arithmetic. */
internal fun responsiveGlanceSizes(presets: List<WidgetResponsivePreset>): Set<DpSize> =
    presets.map { DpSize(it.widthDp.dp, it.heightDp.dp) }.toSet()
