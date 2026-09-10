package com.meteocompare.app.ui.citydetail

import androidx.compose.ui.unit.dp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CityDetailTimelineNavigationTest {
    @Test
    fun timeline_index_matches_items_before_it() {
        assertEquals(1, simplifiedTimelineItemIndex(isOnline = true, hasInsights = false))
        assertEquals(2, simplifiedTimelineItemIndex(isOnline = true, hasInsights = true))
        assertEquals(2, simplifiedTimelineItemIndex(isOnline = false, hasInsights = false))
        assertEquals(3, simplifiedTimelineItemIndex(isOnline = false, hasInsights = true))
    }

    @Test
    fun insight_navigation_falls_back_to_event_peak() {
        val peak = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-08-10T12:00:00Z")
        )
        val event = ForecastEvent(
            kind = ForecastEventKind.PRECIPITATION,
            impact = ForecastInsightLevel.WATCH,
            priority = 10,
            startPoint = peak,
            peakPoint = peak
        )
        val insight = ForecastInsight(
            kind = ForecastInsightKind.RAIN_LIKELY,
            event = event
        )

        assertSame(peak, insightTimelineTarget(insight))
    }

    @Test
    fun hourly_focus_from_insight_targets_the_exact_displayed_hour() {
        val start = Instant.parse("2026-08-10T00:00:00Z")
        val analysis = List(24) { hour ->
            SimplifiedTimelinePoint(instant = start.plusSeconds(hour * 3_600L))
        }
        val display = selectRegularTimelinePoints(analysis)
        val target = analysis[13]

        assertEquals(13, nearestTimelineDisplayIndex(display, target))
        assertSame(target, display[13])
    }

    @Test
    fun `chrono focus keeps one preceding point as visual context`() {
        assertEquals(0, chronoFocusScrollOffset(index = 0, pointWidthPx = 96))
        assertEquals(0, chronoFocusScrollOffset(index = 1, pointWidthPx = 96))
        assertEquals(1_152, chronoFocusScrollOffset(index = 13, pointWidthPx = 96))
    }

    @Test
    fun `chrono fixed label column stays compact on phones and tablets`() {
        assertEquals(104.dp, chronoLabelColumnWidth(screenWidthDp = 412))
        assertEquals(128.dp, chronoLabelColumnWidth(screenWidthDp = 800))
    }

    @Test
    fun `timeline convergence maps to semantic colors and honors divergence`() {
        val high = SimplifiedTimelinePoint(consensusLevel = ModelConsensusLevel.HIGH)
        val divergentHigh = high.copy(divergenceReasons = setOf(DivergenceReason.WIND))

        assertEquals(ModelConsensusLevel.HIGH, timelineConsensusDisplayLevel(high))
        assertEquals(80, timelineConsensusColorAnchor(timelineConsensusDisplayLevel(high)))
        assertEquals(ModelConsensusLevel.MEDIUM, timelineConsensusDisplayLevel(divergentHigh))
        assertEquals(50, timelineConsensusColorAnchor(timelineConsensusDisplayLevel(divergentHigh)))
        assertEquals(0, timelineConsensusColorAnchor(ModelConsensusLevel.LOW))
    }

    @Test
    fun `timeline index inclut la vigilance officielle quand elle est affichee`() {
        assertEquals(2, simplifiedTimelineItemIndex(isOnline = true, hasInsights = false, hasVigilance = true))
        assertEquals(4, simplifiedTimelineItemIndex(isOnline = false, hasInsights = true, hasVigilance = true))
    }

}
