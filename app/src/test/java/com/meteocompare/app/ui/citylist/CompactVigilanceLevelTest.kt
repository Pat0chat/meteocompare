package com.meteocompare.app.ui.citylist

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.VigilanceColor
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.model.VigilanceInterval
import com.meteocompare.app.domain.model.VigilancePeriod
import com.meteocompare.app.domain.model.VigilancePhenomenon
import com.meteocompare.app.domain.model.VigilancePhenomenonAlert
import com.meteocompare.app.domain.model.VigilanceScope
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompactVigilanceLevelTest {
    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val france = City(
        id = "paris", name = "Paris", country = "France", countryCode = "FR",
        latitude = 48.85, longitude = 2.35
    )
    private val foreign = france.copy(country = "Germany", countryCode = "DE")

    private fun vigilance(vararg intervals: VigilanceInterval): VigilanceForecast {
        val severity = intervals.maxByOrNull { it.color.id }?.color ?: VigilanceColor.GREEN
        return VigilanceForecast(
            source = "Météo-France", department = "75", includeCoast = false,
            updateTime = now, productDatetime = now, generationTimestamp = now,
            fetchedAt = now, evaluationTime = now,
            periods = listOf(VigilancePeriod(
                term = "J", begin = now, end = now.plusSeconds(86400),
                maxColor = severity, departmentMaxColor = severity, coastMaxColor = null,
                phenomena = listOf(VigilancePhenomenonAlert(
                    phenomenon = VigilancePhenomenon.WIND,
                    maxColor = severity, intervals = intervals.toList()
                ))
            ))
        )
    }

    private fun interval(color: VigilanceColor, end: Instant? = now.plusSeconds(3600)) =
        VigilanceInterval(
            begin = now.minusSeconds(600), end = end,
            color = color, scope = VigilanceScope.DEPARTMENT
        )

    private fun state(city: City, forecast: VigilanceForecast?) =
        CityCardState(city, ForecastState.Loading, vigilance = forecast)

    @Test fun shows_highest_active_level_for_french_city() {
        val data = vigilance(
            interval(VigilanceColor.YELLOW),
            interval(VigilanceColor.RED),
            interval(VigilanceColor.ORANGE)
        )
        assertEquals(VigilanceColor.RED, state(france, data).compactVigilanceLevel())
    }

    @Test fun hides_level_for_foreign_city_even_with_an_alert() {
        assertNull(state(foreign, vigilance(interval(VigilanceColor.ORANGE))).compactVigilanceLevel())
    }

    @Test fun hides_green_missing_and_expired_alerts() {
        assertNull(state(france, null).compactVigilanceLevel())
        assertNull(state(france, vigilance(interval(VigilanceColor.GREEN))).compactVigilanceLevel())
        assertNull(
            state(france, vigilance(interval(VigilanceColor.RED, now.minusSeconds(20))))
                .compactVigilanceLevel()
        )
    }

    @Test fun future_warning_is_kept_like_the_expanded_card() {
        val future = VigilanceInterval(
            begin = now.plusSeconds(3600), end = now.plusSeconds(7200),
            color = VigilanceColor.ORANGE, scope = VigilanceScope.DEPARTMENT
        )
        assertEquals(VigilanceColor.ORANGE, state(france, vigilance(future)).compactVigilanceLevel())
    }
}
