package com.meteocompare.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** A bounded scale is shared by every Glance forecast and editorial text. */
internal val LocalWidgetTextScale = staticCompositionLocalOf { 1f }

/** Use only for Glance TextStyle; icon drawings and layout dp remain unchanged. */
@Composable
internal fun TextUnit.widgetScaled(): TextUnit = (value * LocalWidgetTextScale.current).sp
