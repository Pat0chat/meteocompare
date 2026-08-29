package com.meteocompare.app.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
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
        composeRule.onNodeWithTag(TAG_VIGILANCE_DETAIL).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_VIGILANCE_MARINE).performScrollTo().assertIsDisplayed()
    }

    private fun forecast(): VigilanceForecast {
        val start = Instant.parse("2026-08-29T08:00:00Z")
        val end = Instant.parse("2026-08-29T20:00:00Z")
        val storms = VigilancePhenomenonAlert(
            phenomenon = VigilancePhenomenon.THUNDERSTORMS,
            maxColor = VigilanceColor.ORANGE,
            intervals = listOf(
                VigilanceInterval(start, end, VigilanceColor.ORANGE, VigilanceScope.DEPARTMENT)
            )
        )
        val coast = VigilancePhenomenonAlert(
            phenomenon = VigilancePhenomenon.COASTAL_FLOODING,
            maxColor = VigilanceColor.YELLOW,
            intervals = listOf(
                VigilanceInterval(start, end, VigilanceColor.YELLOW, VigilanceScope.COAST)
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
                    end = end,
                    maxColor = VigilanceColor.ORANGE,
                    departmentMaxColor = VigilanceColor.ORANGE,
                    coastMaxColor = VigilanceColor.YELLOW,
                    phenomena = listOf(storms, coast)
                )
            ),
            fetchedAt = start
        )
    }
}
