package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastInsightResourcesTest {
    @Test
    fun `precipitation title reflects the phenomenon and its evolution`() {
        assertEquals(
            R.string.forecast_insight_title_weather_thunderstorm,
            likelyPrecipitationTitleRes(
                ForecastInsight(
                    kind = ForecastInsightKind.RAIN_LIKELY,
                    targetCondition = WeatherCondition.THUNDERSTORM
                )
            )
        )
        assertEquals(
            R.string.forecast_insight_title_rain_strengthening,
            likelyPrecipitationTitleRes(
                ForecastInsight(
                    kind = ForecastInsightKind.RAIN_LIKELY,
                    referencePoint = SimplifiedTimelinePoint(),
                    referenceValue = 30,
                    targetValue = 70
                )
            )
        )
    }

    @Test
    fun `disagreement title follows the shared reason priority`() {
        val reasons = setOf(DivergenceReason.CONDITION, DivergenceReason.PRECIPITATION)

        assertEquals(DivergenceReason.PRECIPITATION, primaryDivergenceReason(reasons))
        assertEquals(
            R.string.forecast_insight_title_disagreement_rain,
            disagreementTitleRes(reasons)
        )
    }

    @Test
    fun `wind title distinguishes a rise from a very strong event`() {
        assertEquals(
            R.string.forecast_insight_title_wind_rising,
            windEventTitleRes(
                ForecastInsight(
                    kind = ForecastInsightKind.WIND_EVENT,
                    value = 20,
                    secondaryValue = 40
                )
            )
        )
        assertEquals(
            R.string.forecast_insight_title_wind_very_strong,
            windEventTitleRes(
                ForecastInsight(
                    kind = ForecastInsightKind.WIND_EVENT,
                    value = 20,
                    secondaryValue = 65
                )
            )
        )
    }

    @Test
    fun `temperature title keeps safety thresholds ahead of uncertainty`() {
        val insight = ForecastInsight(
            kind = ForecastInsightKind.TEMPERATURE_CHANGE,
            targetValue = -2,
            divergenceReasons = setOf(DivergenceReason.TEMPERATURE)
        )

        assertEquals(
            R.string.forecast_insight_title_temperature_frost,
            temperatureChangeTitleRes(insight)
        )
    }

    @Test
    fun `weather change title recognizes an improvement`() {
        val insight = ForecastInsight(
            kind = ForecastInsightKind.WEATHER_CHANGE,
            level = ForecastInsightLevel.WATCH,
            referenceCondition = WeatherCondition.THUNDERSTORM,
            targetCondition = WeatherCondition.CLEAR
        )

        assertEquals(
            R.string.forecast_insight_title_weather_improving,
            weatherChangeTitleRes(insight)
        )
    }
}
