package com.meteocompare.app.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.meteocompare.app.domain.model.VigilanceColor
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.model.VigilanceInterval
import com.meteocompare.app.domain.model.VigilancePeriod
import com.meteocompare.app.domain.model.VigilancePhenomenon
import com.meteocompare.app.domain.model.VigilancePhenomenonAlert
import com.meteocompare.app.domain.model.VigilanceScope
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class VigilanceCardsRenderTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun home_detail_and_coastal_variants_render() {
        val vigilance = forecast()
        val coastal = vigilance.coastalFloodingAlert

        composeRule.setContent {
            MeteoCompareTheme(dynamicColor = false) {
                LazyColumn {
                    item { VigilanceCompactBanner(vigilance, "Europe/Paris") }
                    item { VigilanceDetailCard(vigilance, "Europe/Paris") }
                    item { MarineCoastalVigilanceBanner(coastal, "Europe/Paris") }
                }
            }
        }

        composeRule.onNodeWithTag(TAG_VIGILANCE_HOME).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_VIGILANCE_HOME_TEXT)
            .assertTextContains("29", substring = true)
            .assertTextContains("10h–14h, 18h–22h", substring = true)
        composeRule.onNodeWithTag(TAG_VIGILANCE_DETAIL).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_VIGILANCE_ALERT_TIMING_PREFIX + VigilancePhenomenon.THUNDERSTORMS.id)
            .assertTextContains("29", substring = true)
            .assertTextContains("10h–14h, 18h–22h", substring = true)
        composeRule.onNodeWithTag(TAG_VIGILANCE_ALERT_TIMING_PREFIX + VigilancePhenomenon.WIND.id)
            .performScrollTo()
            .assertTextContains("29", substring = true)
            .assertTextContains("20h", substring = true)
            .assertTextContains("30", substring = true)
            .assertTextContains("04h", substring = true)
            .assertTextContains("→", substring = true)
        composeRule.onNodeWithTag(TAG_VIGILANCE_MARINE).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun home_badge_displays_both_dates_when_period_crosses_midnight() {
        val base = forecast()
        val period = base.periods.single()
        val wind = period.phenomena.single { it.phenomenon == VigilancePhenomenon.WIND }
        val vigilance = base.copy(
            periods = listOf(
                period.copy(
                    maxColor = VigilanceColor.YELLOW,
                    departmentMaxColor = VigilanceColor.YELLOW,
                    coastMaxColor = null,
                    phenomena = listOf(wind)
                )
            )
        )

        composeRule.setContent {
            MeteoCompareTheme(dynamicColor = false) {
                VigilanceCompactBanner(vigilance, "Europe/Paris")
            }
        }

        composeRule.onNodeWithTag(TAG_VIGILANCE_HOME_TEXT)
            .assertTextContains("29", substring = true)
            .assertTextContains("20h", substring = true)
            .assertTextContains("30", substring = true)
            .assertTextContains("04h", substring = true)
            .assertTextContains("→", substring = true)
    }

    private fun forecast(): VigilanceForecast {
        val start = Instant.parse("2026-08-29T08:00:00Z")
        val end = Instant.parse("2026-08-29T20:00:00Z")
        val windEnd = Instant.parse("2026-08-30T02:00:00Z")
        val storms = VigilancePhenomenonAlert(
            phenomenon = VigilancePhenomenon.THUNDERSTORMS,
            maxColor = VigilanceColor.ORANGE,
            intervals = listOf(
                VigilanceInterval(
                    start,
                    Instant.parse("2026-08-29T12:00:00Z"),
                    VigilanceColor.ORANGE,
                    VigilanceScope.DEPARTMENT
                ),
                VigilanceInterval(
                    Instant.parse("2026-08-29T16:00:00Z"),
                    end,
                    VigilanceColor.ORANGE,
                    VigilanceScope.DEPARTMENT
                )
            )
        )
        val coast = VigilancePhenomenonAlert(
            phenomenon = VigilancePhenomenon.COASTAL_FLOODING,
            maxColor = VigilanceColor.YELLOW,
            intervals = listOf(
                VigilanceInterval(start, end, VigilanceColor.YELLOW, VigilanceScope.COAST)
            )
        )
        val wind = VigilancePhenomenonAlert(
            phenomenon = VigilancePhenomenon.WIND,
            maxColor = VigilanceColor.YELLOW,
            intervals = listOf(
                VigilanceInterval(
                    Instant.parse("2026-08-29T18:00:00Z"),
                    windEnd,
                    VigilanceColor.YELLOW,
                    VigilanceScope.DEPARTMENT
                )
            )
        )
        return VigilanceForecast(
            source = "Météo-France",
            department = "29",
            includeCoast = true,
            updateTime = start,
            productDatetime = start,
            generationTimestamp = start,
            periods = listOf(
                VigilancePeriod(
                    term = "J",
                    begin = start,
                    end = windEnd,
                    maxColor = VigilanceColor.ORANGE,
                    departmentMaxColor = VigilanceColor.ORANGE,
                    coastMaxColor = VigilanceColor.YELLOW,
                    phenomena = listOf(storms, coast, wind)
                )
            ),
            fetchedAt = start
        )
    }
}
