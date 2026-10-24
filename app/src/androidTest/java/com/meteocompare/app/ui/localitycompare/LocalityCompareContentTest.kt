package com.meteocompare.app.ui.localitycompare

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.model.ClockFormatPreference
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LocalityCompareContentTest {
    @get:Rule val compose = createComposeRule()
    // Stable across recompositions: otherwise remember(calculator) rebuilds the
    // comparison cache and restarts its asynchronous preparation after every click.
    private val calculator = ConfidenceCalculator(EqualWeighting())
    private val cities = listOf(
        City("paris", "Paris", null, "France", 48.85, 2.35, "Europe/Paris"),
        City("lyon", "Lyon", null, "France", 45.75, 4.85, "Europe/Paris"),
        City("lille", "Lille", null, "France", 50.63, 3.05, "Europe/Paris")
    )

    private fun waitForComparison() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("compare_overview").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * The comparison has a full overview and a 24-hour chart before its disclosure.
     * On a phone the details button is usually below the viewport. Compose Test's
     * performClick() does not implicitly scroll a vertically scrolling Column.
     * Explicitly bring the target into view and prove the real click opened it.
     */
    private fun openComparisonDetails() {
        compose.onNodeWithTag("compare_hour_details_collapsed", useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithTag("compare_toggle_details")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithTag("compare_hour_details_expanded", useUnmergedTree = true)
            .assertExists()
    }

    /** LazyRow does not compose off-screen metric chips; explicitly scroll to a metric
     * before attempting a direct click. The overview selection itself auto-reveals it. */
    private fun revealChartMetric(metric: CompareChartMetric) {
        compose.onNodeWithTag("compare_chart_metric_list")
            .performScrollTo()
            .performScrollToIndex(metric.ordinal)
        compose.onNodeWithTag("compare_chart_metric_${metric.name.lowercase()}")
            .assertIsDisplayed()
    }

    /** Assert the actual disclosed value, not a transient parent semantics tag. */
    private fun waitForHourlyDetail(tag: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun chartAxesShowUnitsAndUpdateWithTheMetricAndUkProfile() {
        compose.setContent {
            var metric by remember { mutableStateOf(CompareChartMetric.TEMPERATURE) }
            var unitSystem by remember { mutableStateOf(UnitSystem.UK_MIXED) }
            MaterialTheme {
                Column {
                    Row {
                        Button(onClick = { metric = CompareChartMetric.WIND },
                            modifier = Modifier.testTag("chart_test_wind")) { Text("Wind") }
                        Button(onClick = { metric = CompareChartMetric.RAIN },
                            modifier = Modifier.testTag("chart_test_rain")) { Text("Rain") }
                    }
                    Row {
                        Button(onClick = { metric = CompareChartMetric.UV },
                            modifier = Modifier.testTag("chart_test_uv")) { Text("UV") }
                        Button(onClick = { unitSystem = UnitSystem.IMPERIAL },
                            modifier = Modifier.testTag("chart_test_imperial")) { Text("Imperial") }
                    }
                    LocalityHourlyChart(
                        cities = listOf(HourChartCity("paris", "Paris", List(24) { null })),
                        units = WeatherUnits(unitSystem), locale = Locale.UK,
                        hourFormatter = WeatherTimeFormat.hourPadded(Locale.UK, ClockFormatPreference.HOUR_12),
                        selectedHour = 14, onHourSelected = {},
                        metric = metric, onMetricSelected = { metric = it }
                    )
                }
            }
        }
        compose.onNodeWithTag("compare_chart_axis_unit").assertTextContains("°C", substring = true)
        compose.onNodeWithTag("chart_test_wind").performClick()
        compose.onNodeWithTag("compare_chart_axis_unit").assertTextContains("mph", substring = true)
        compose.onNodeWithTag("chart_test_rain").performClick()
        compose.onNodeWithTag("compare_chart_axis_unit").assertTextContains("mm", substring = true)
        compose.onNodeWithTag("chart_test_imperial").performClick()
        compose.onNodeWithTag("compare_chart_axis_unit").assertTextContains("in", substring = true)
        compose.onNodeWithTag("chart_test_uv").performClick()
        compose.onNodeWithTag("compare_chart_axis_unit").assertTextContains("UV", substring = true)
        compose.onNodeWithTag("compare_selected_hour").assertTextEquals("2 PM")
    }

    @Test fun hourDetailsDisclosureHasStableSemanticsAndActuallyToggles() {
        compose.setContent {
            var expanded by remember { mutableStateOf(false) }
            MaterialTheme {
                LocalityHourDetails(
                    cities = listOf(HourChartCity("paris", "Paris", List(24) { null })),
                    selectedHour = 14,
                    hourLabel = "14h",
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    units = WeatherUnits(),
                    locale = Locale.FRANCE
                )
            }
        }
        compose.onNodeWithTag("compare_hour_details_collapsed").assertExists()
        compose.onNodeWithTag("compare_toggle_details").performClick()
        compose.onNodeWithTag("compare_hour_details_expanded").assertExists()
        compose.onNodeWithTag("compare_detail_uv_paris", useUnmergedTree = true)
            .assertTextEquals("—")
        compose.onNodeWithTag("compare_toggle_details").performClick()
        compose.onNodeWithTag("compare_hour_details_collapsed").assertExists()
        compose.onNodeWithTag("compare_detail_uv_paris", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test fun expandedDetailsStayMountedWhileForecastSnapshotChanges() {
        val date = LocalDate.of(2026, 10, 14)
        var comparisonState by mutableStateOf(
            LocalityCompareState(
                favorites = cities,
                selectedIds = listOf("paris", "lyon"),
                forecasts = mapOf("paris" to CompareForecastState.Loading)
            )
        )
        compose.setContent {
            MaterialTheme {
                LocalityCompareContent(
                    state = comparisonState,
                    calculator = calculator,
                    onBack = {}, onToggleCity = {}, onRefresh = {},
                    currentDate = date, currentHour = 14
                )
            }
        }
        waitForComparison()
        openComparisonDetails()
        // A new asynchronous preparation must never unmount the disclosure.
        compose.runOnIdle {
            comparisonState = comparisonState.copy(
                forecasts = mapOf(
                    "paris" to CompareForecastState.Failed("offline"),
                    "lyon" to CompareForecastState.Loading
                )
            )
        }
        compose.onNodeWithTag("compare_hour_details_expanded", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("compare_detail_uv_paris", useUnmergedTree = true)
            .assertTextEquals("—")
    }

    @Test fun selectedCities_renderInParallelEvenWhenAForecastIsUnavailable() {
        compose.setContent {
            MaterialTheme {
                LocalityCompareContent(
                    state = LocalityCompareState(
                        favorites = cities,
                        selectedIds = listOf("paris", "lyon"),
                        forecasts = mapOf(
                            "paris" to CompareForecastState.Failed("offline"),
                            "lyon" to CompareForecastState.Loading
                        )
                    ),
                    calculator = calculator,
                    onBack = {}, onToggleCity = {}, onRefresh = {},
                    currentDate = LocalDate.of(2026, 10, 14)
                )
            }
        }
        waitForComparison()
        compose.onNodeWithTag("compare_column_paris").assertExists()
        compose.onNodeWithTag("compare_column_lyon").assertExists()
        compose.onNodeWithTag("compare_city_lille").assertExists()
    }

    @Test fun uvShowsDailyMaximumThenHourlyValueAndKeepsClockPreference() {
        val date = LocalDate.of(2026, 6, 20)
        val zone = ZoneId.of("Europe/Paris")
        val instant = LocalDateTime.of(2026, 6, 20, 14, 0).atZone(zone).toInstant()
        val city = cities.first()
        val hourly = HourlyForecast(
            timestamps = listOf(instant), temperature2m = listOf(21.0),
            precipitation = listOf(0.0), windSpeed10m = listOf(14.0),
            cloudCover = listOf(25)
        )
        val daily = DailyForecast(
            dates = listOf(date), tempMax = listOf(23.0), tempMin = listOf(16.0),
            precipitationSum = listOf(0.0), windSpeedMax = listOf(20.0)
        )
        val forecast = CityForecast(city, mapOf(WeatherModel.GFS to
            ForecastSeries(WeatherModel.GFS, hourly, daily)))
        // Keep test input identity stable across Compose recompositions, as in the
        // production StateFlow. In particular UV snapshots must not be recreated.
        val comparisonState = LocalityCompareState(
            favorites = cities, selectedIds = listOf("paris", "lyon"),
            forecasts = mapOf("paris" to CompareForecastState.Ready(forecast)),
            uvForecasts = mapOf("paris" to UvForecast(
                mapOf(instant to 5.6), mapOf(date to 8.3)
            ))
        )
        compose.setContent {
            CompositionLocalProvider(LocalClockFormatPreference provides ClockFormatPreference.HOUR_12) {
                MaterialTheme {
                    LocalityCompareContent(
                        state = comparisonState,
                        calculator = calculator,
                        onBack = {}, onToggleCity = {}, onRefresh = {},
                        currentDate = date, currentHour = 14
                    )
                }
            }
        }
        waitForComparison()
        compose.onNodeWithTag("compare_uv_paris").assertTextContains("8", substring = true)
        // The daily overview and hourly chart share one selected date; no mode switch.
        compose.onNodeWithTag("compare_hour_chart").assertExists()
        compose.onNodeWithTag("compare_hour_chart")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        compose.onNodeWithTag("compare_selected_hour").assertTextEquals("10 AM")
        compose.onNodeWithTag("compare_hour_chart")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(14f) }
        compose.onNodeWithTag("compare_selected_hour").assertTextEquals("2 PM")
        revealChartMetric(CompareChartMetric.RAIN)
        compose.onNodeWithTag("compare_chart_metric_rain").performClick()
        compose.onNodeWithTag("compare_chart_value_paris").assertTextContains("mm", substring = true)
        openComparisonDetails()
        waitForHourlyDetail("compare_detail_uv_paris")
        compose.onNodeWithTag("compare_detail_uv_paris", useUnmergedTree = true)
            .assertTextContains("5", substring = true)
        compose.onNodeWithTag("compare_detail_clouds_paris", useUnmergedTree = true)
            .assertTextEquals("25 %")
        compose.onNodeWithTag("compare_column_lyon").assertExists()
        // Touching an hourly value never changes the daily summary.
        compose.onNodeWithTag("compare_uv_paris").assertTextContains("8", substring = true)
    }

    @Test fun chartSelectionSnapsToValidHoursAndStaysWithinTheLocalDay() {
        assertEquals(0, chartHourFromX(-50f, 10f, 240f))
        assertEquals(0, chartHourFromX(10f, 10f, 240f))
        assertEquals(12, chartHourFromX(130f, 10f, 240f))
        assertEquals(23, chartHourFromX(500f, 10f, 240f))
        assertEquals(0, chartHourFromX(10f, 10f, 10f))
    }

    @Test fun overviewMetricSelectsTimelineWithoutSwitchingModes() {
        val date = LocalDate.of(2026, 10, 14)
        val city = cities.first()
        val instant = date.atTime(14, 0).atZone(ZoneId.of("Europe/Paris")).toInstant()
        val forecast = CityForecast(city, mapOf(WeatherModel.GFS to ForecastSeries(
            WeatherModel.GFS,
            HourlyForecast(
                timestamps = listOf(instant), temperature2m = listOf(19.0),
                precipitation = listOf(2.0), windSpeed10m = listOf(12.0),
                cloudCover = listOf(45)
            ),
            DailyForecast(
                dates = listOf(date), tempMax = listOf(20.0), tempMin = listOf(12.0),
                precipitationSum = listOf(2.0), windSpeedMax = listOf(14.0)
            )
        )))
        val comparisonState = LocalityCompareState(
            favorites = cities,
            selectedIds = listOf("paris", "lyon"),
            forecasts = mapOf("paris" to CompareForecastState.Ready(forecast))
        )
        compose.setContent {
            MaterialTheme {
                LocalityCompareContent(
                    state = comparisonState,
                    calculator = calculator,
                    onBack = {}, onToggleCity = {}, onRefresh = {},
                    currentDate = date, currentHour = 14
                )
            }
        }
        waitForComparison()
        compose.onNodeWithTag("compare_overview_metric_rain").performClick()
        compose.onNodeWithTag("compare_hour_chart").assertExists()
        // The overview must reveal the selected chip even if it was initially
        // outside LazyRow's composed viewport.
        compose.onNodeWithTag("compare_chart_metric_list").performScrollTo()
        compose.onNodeWithTag("compare_chart_metric_rain")
            .assertIsDisplayed()
            .assertIsSelected()
        compose.onNodeWithTag("compare_detail_rain_paris", useUnmergedTree = true).assertDoesNotExist()
        openComparisonDetails()
        waitForHourlyDetail("compare_detail_rain_paris")
        compose.onNodeWithTag("compare_detail_rain_paris", useUnmergedTree = true)
            .assertTextContains("2", substring = true)
        compose.onNodeWithTag("compare_detail_rain_lyon", useUnmergedTree = true)
            .assertTextEquals("—")
    }

    @Test fun dateMetricAndHourChangesNeverRequestANetworkRefresh() {
        val today = LocalDate.of(2026, 10, 14)
        val tomorrow = today.plusDays(1)
        val zone = ZoneId.of("Europe/Paris")
        val hourly = HourlyForecast(
            timestamps = (0L until 48).map { today.atStartOfDay(zone).toInstant().plusSeconds(it * 3600) },
            temperature2m = List(48) { 17.0 }, precipitation = List(48) { 0.0 },
            windSpeed10m = List(48) { 10.0 }, cloudCover = List(48) { 25 }
        )
        val daily = DailyForecast(
            dates = listOf(today, tomorrow), tempMax = listOf(20.0, 22.0),
            tempMin = listOf(12.0, 13.0), precipitationSum = listOf(0.0, 1.0),
            windSpeedMax = listOf(14.0, 12.0)
        )
        val forecast = CityForecast(cities[0], mapOf(WeatherModel.GFS to
            ForecastSeries(WeatherModel.GFS, hourly, daily)))
        var refreshCount = 0
        compose.setContent {
            MaterialTheme {
                LocalityCompareContent(
                    state = LocalityCompareState(
                        favorites = cities, selectedIds = listOf("paris", "lyon"),
                        forecasts = mapOf("paris" to CompareForecastState.Ready(forecast))
                    ),
                    calculator = calculator,
                    onBack = {}, onToggleCity = {}, onRefresh = { refreshCount++ },
                    currentDate = today, currentHour = 12
                )
            }
        }
        waitForComparison()
        compose.onNodeWithTag("compare_day_$tomorrow").performClick()
        waitForComparison()
        compose.onNodeWithTag("compare_overview_metric_uv").performClick()
        compose.onNodeWithTag("compare_hour_chart")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(16f) }
        compose.runOnIdle { assertEquals(0, refreshCount) }
        compose.onNodeWithTag("locality_compare_refresh").performClick()
        compose.runOnIdle { assertEquals(1, refreshCount) }
    }

    @Test fun thirdFavoriteCanBeSelected() {
        var lastCity: String? = null
        compose.setContent {
            MaterialTheme {
                LocalityCompareContent(
                    state = LocalityCompareState(favorites = cities, selectedIds = listOf("paris", "lyon")),
                    calculator = calculator,
                    onBack = {}, onToggleCity = { lastCity = it }, onRefresh = {},
                    currentDate = LocalDate.of(2026, 10, 14)
                )
            }
        }
        compose.onNodeWithTag("compare_city_lille").performClick()
        compose.runOnIdle { assertEquals("lille", lastCity) }
    }

    @Test fun rainChanceAndCloudCover_renderPerCityAndRespectMissingData() {
        val date = LocalDate.of(2026, 10, 14)
        fun ready(city: City, clouds: List<Int?>, probability: Int?): CompareForecastState.Ready {
            val start = date.atStartOfDay(java.time.ZoneId.of(city.timezone!!)).toInstant()
            val hourly = HourlyForecast(
                timestamps = (0L until 24).map { start.plusSeconds(it * 3600) },
                temperature2m = List(24) { 16.0 }, precipitation = List(24) { 0.0 },
                windSpeed10m = List(24) { 10.0 }, cloudCover = clouds
            )
            val daily = DailyForecast(
                dates = listOf(date), tempMax = listOf(20.0), tempMin = listOf(10.0),
                precipitationSum = listOf(if (probability == null) null else 3.0),
                windSpeedMax = listOf(10.0),
                precipitationProbabilityMax = listOf(probability)
            )
            val series = ForecastSeries(WeatherModel.GFS, hourly, daily)
            return CompareForecastState.Ready(CityForecast(city, mapOf(WeatherModel.GFS to series)))
        }
        compose.setContent {
            MaterialTheme {
                LocalityCompareContent(
                    state = LocalityCompareState(
                        favorites = cities,
                        selectedIds = listOf("paris", "lyon"),
                        forecasts = mapOf(
                            "paris" to ready(cities[0], List(24) { 60 }, 70),
                            "lyon" to ready(cities[1], emptyList(), null)
                        )
                    ),
                    calculator = calculator,
                    onBack = {}, onToggleCity = {}, onRefresh = {}, currentDate = date
                )
            }
        }
        waitForComparison()
        compose.onNodeWithTag("compare_cloud_cover_paris").assertTextEquals("60 %")
        compose.onNodeWithTag("compare_cloud_bar_paris").assertExists()
        compose.onNodeWithTag("compare_rain_bar_paris").assertExists()
        compose.onNodeWithTag("compare_cloud_cover_lyon").assertTextEquals("—")
        compose.onNodeWithTag("compare_rain_probability_paris").assertTextEquals("70 %")
        // Lyon has neither daily rain amount nor probability: no consensus estimate is possible.
        compose.onNodeWithTag("compare_rain_probability_lyon").assertTextEquals("—")
        // Absent data must never masquerade as a zero-percent indicator.
        compose.onNodeWithTag("compare_cloud_bar_lyon").assertDoesNotExist()
        compose.onNodeWithTag("compare_rain_bar_lyon").assertDoesNotExist()
    }
}

