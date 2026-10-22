package com.meteocompare.app.ui.citylist

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.meteocompare.app.data.repository.CurrentLocationStore
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CurrentLocationCardRefreshTest {
    @get:Rule val composeRule = createComposeRule()

    private val currentCity = TestFixtures.paris.copy(
        id = CurrentLocationStore.idFor(980, 46),
        name = "Ma position"
    )
    private val forecast = ForecastState.Loaded(
        today = DayConfidence(
            date = TestFixtures.today,
            tempMax = ConfidenceScore(80, 20.0, 22.0, 21.0, 1.0, 5),
            tempMin = null,
            precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
            windMax = ConfidenceScore(80, 8.0, 12.0, 10.0, 1.0, 5)
        ),
        currentTemp = 20.0,
        fetchedAt = Instant.now()
    )

    @Test fun location_timestamp_click_refreshes_only_location_not_entire_card() {
        var locationRefreshes = 0
        var cardOpens = 0
        val locationState = androidx.compose.runtime.mutableLongStateOf(1_760_000_000_000L)
        composeRule.setContent {
            MeteoCompareTheme {
                CityCard(
                    state = CityCardState(currentCity, forecast),
                    expanded = true,
                    onClick = { cardOpens++ },
                    onRemove = {}, onRetry = {},
                    currentLocationUpdatedAt = locationState.longValue,
                    onCurrentLocationRefresh = { locationRefreshes++ }
                )
            }
        }
        composeRule.onNodeWithTag(TAG_HOME_POSITION_REFRESH, useUnmergedTree = true)
            .assertExists().performClick()
        composeRule.runOnIdle {
            assertEquals(1, locationRefreshes)
            assertEquals("The timestamp must not open the detail card", 0, cardOpens)
            locationState.longValue += 60_000L
        }
        composeRule.onNodeWithTag(TAG_HOME_POSITION_REFRESH, useUnmergedTree = true).assertExists()
    }

    @Test fun a_fixed_city_never_displays_the_current_location_refresh_button() {
        composeRule.setContent {
            MeteoCompareTheme {
                CityCard(
                    state = CityCardState(TestFixtures.paris, forecast),
                    expanded = true, onClick = {}, onRemove = {}, onRetry = {},
                    currentLocationUpdatedAt = 1_760_000_000_000L,
                    onCurrentLocationRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_HOME_POSITION_REFRESH, useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
