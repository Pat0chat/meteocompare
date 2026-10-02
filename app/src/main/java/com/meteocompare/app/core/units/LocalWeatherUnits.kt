package com.meteocompare.app.core.units

import androidx.compose.runtime.staticCompositionLocalOf

/** Shared by Compose and Glance; no mutable global preference or converted domain copies. */
val LocalWeatherUnits = staticCompositionLocalOf { WeatherUnits() }
