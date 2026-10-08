package com.meteocompare.app.widget

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.meteocompare.app.domain.model.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetConfigurationTest {
    private val paris = City("paris", "Paris", country = "France", latitude = 48.85, longitude = 2.35)
    private val lyon = paris.copy(id = "lyon", name = "Lyon")

    @Test fun new_widget_uses_defaults_and_first_favorite() {
        val config = WidgetConfiguration.fromPreferences(emptyPreferences())
        assertEquals(WidgetConfiguration(), config)
        assertEquals(paris.id, config.selectableCityId(listOf(paris, lyon)))
        assertNull(config.selectableCityId(emptyList()))
    }

    @Test fun existing_widget_restores_all_settings_and_its_own_city() {
        val config = WidgetConfiguration.fromPreferences(preferencesOf(
            WidgetPreferences.CityIdKey to lyon.id,
            WidgetPreferences.OpacityPctKey to 35,
            WidgetPreferences.ForecastModeKey to ForecastMode.HEATMAP_TREND_12H.name,
            WidgetPreferences.BackgroundColorKey to 0xFF123456.toInt(),
            WidgetPreferences.TextColorKey to 0xFFFFFFFF.toInt()
        ))
        assertEquals(WidgetConfiguration(lyon.id, 35, ForecastMode.HEATMAP_TREND_12H,
            0xFF123456.toInt(), 0xFFFFFFFF.toInt()), config)
        assertEquals(lyon.id, config.selectableCityId(listOf(paris, lyon)))
    }

    @Test fun deleted_city_is_not_silently_replaced_by_first_favorite() {
        val config = WidgetConfiguration(cityId = lyon.id)
        assertNull(config.selectableCityId(listOf(paris)))
    }

    @Test fun legacy_modes_are_normalized_and_invalid_values_use_safe_defaults() {
        listOf(ForecastMode.CONFIDENCE_TEMPERATURE, ForecastMode.CONFIDENCE_PRECIPITATION,
            ForecastMode.CONFIDENCE_WIND).forEach { mode ->
            assertEquals(ForecastMode.CONFIDENCE_ALL, WidgetConfiguration.fromPreferences(
                preferencesOf(WidgetPreferences.ForecastModeKey to mode.name)).forecastMode)
        }
        val invalid = WidgetConfiguration.fromPreferences(preferencesOf(
            WidgetPreferences.ForecastModeKey to "unknown",
            WidgetPreferences.OpacityPctKey to 150
        ))
        assertEquals(ForecastMode.HOURLY, invalid.forecastMode)
        assertEquals(100, invalid.opacityPct)
        assertEquals(0, WidgetConfiguration.fromPreferences(preferencesOf(
            WidgetPreferences.OpacityPctKey to -50)).opacityPct)
    }

    @Test fun automatic_colors_and_explicit_transparent_color_remain_distinct() {
        val automatic = WidgetConfiguration.fromPreferences(emptyPreferences())
        assertNull(automatic.backgroundColorArgb)
        assertNull(automatic.textColorArgb)
        assertEquals(0, WidgetConfiguration.fromPreferences(preferencesOf(
            WidgetPreferences.BackgroundColorKey to 0)).backgroundColorArgb)
    }

    @Test fun separate_widget_preferences_remain_independent() {
        val first = WidgetConfiguration.fromPreferences(preferencesOf(
            WidgetPreferences.CityIdKey to lyon.id, WidgetPreferences.OpacityPctKey to 20))
        val second = WidgetConfiguration.fromPreferences(preferencesOf(
            WidgetPreferences.CityIdKey to paris.id, WidgetPreferences.OpacityPctKey to 80))
        assertEquals(lyon.id, first.cityId)
        assertEquals(20, first.opacityPct)
        assertEquals(paris.id, second.cityId)
        assertEquals(80, second.opacityPct)
    }
    @Test fun custom_colors_and_square_corners_round_trip_without_touching_refresh() {
        val prefs = preferencesOf(
            WidgetPreferences.CityIdKey to lyon.id,
            WidgetPreferences.OpacityPctKey to 40,
            WidgetPreferences.RefreshTickKey to 123456789L,
            WidgetPreferences.LastDispatchAtKey to 222333444L,
            WidgetPreferences.ForecastModeKey to ForecastMode.DAILY.name
        ).toMutablePreferences()
        val configuration = WidgetConfiguration(
            cityId = lyon.id, opacityPct = 40, forecastMode = ForecastMode.DAILY,
            backgroundColorArgb = 0xFF3264AB.toInt(), textColorArgb = 0xFFDDEE33.toInt(),
            cornerStyle = WidgetCornerStyle.SQUARE
        )
        configuration.writeTo(prefs)
        assertEquals(configuration, WidgetConfiguration.fromPreferences(prefs))
        assertEquals(123456789L, prefs[WidgetPreferences.RefreshTickKey])
        assertEquals(222333444L, prefs[WidgetPreferences.LastDispatchAtKey])

        // Simule une réédition qui laisse tout intact.
        WidgetConfiguration.fromPreferences(prefs).writeTo(prefs)
        assertEquals(configuration, WidgetConfiguration.fromPreferences(prefs))
        assertEquals(123456789L, prefs[WidgetPreferences.RefreshTickKey])
        assertEquals(222333444L, prefs[WidgetPreferences.LastDispatchAtKey])

        // Retour en mode Auto et arrondi ; aucune clé métier affectée.
        configuration.copy(backgroundColorArgb = null, textColorArgb = null,
            cornerStyle = WidgetCornerStyle.ROUNDED).writeTo(prefs)
        assertNull(prefs[WidgetPreferences.BackgroundColorKey])
        assertNull(prefs[WidgetPreferences.TextColorKey])
        assertEquals(WidgetCornerStyle.ROUNDED,
            WidgetConfiguration.fromPreferences(prefs).cornerStyle)
        assertEquals(123456789L, prefs[WidgetPreferences.RefreshTickKey])
    }

    @Test fun legacy_and_unknown_corner_values_default_to_rounded() {
        assertEquals(WidgetCornerStyle.ROUNDED,
            WidgetConfiguration.fromPreferences(emptyPreferences()).cornerStyle)
        assertEquals(WidgetCornerStyle.ROUNDED,
            WidgetConfiguration.fromPreferences(preferencesOf(
                WidgetPreferences.CornerStyleKey to "CORRUPTED")).cornerStyle)
        assertEquals(WidgetCornerStyle.SQUARE,
            WidgetConfiguration.fromPreferences(preferencesOf(
                WidgetPreferences.CornerStyleKey to "SQUARE")).cornerStyle)
    }
}
