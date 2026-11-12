package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetForecastCardLayoutTest {

    @Test
    fun `Glance rows never exceed ten direct children for four or five forecasts`() {
        assertEquals(0, forecastTimelineDirectChildren(0))
        assertEquals(1, forecastTimelineDirectChildren(1))
        assertEquals(4, forecastTimelineDirectChildren(4))
        assertEquals(5, forecastTimelineDirectChildren(5))
        assertTrue(forecastTimelineDirectChildren(MAX_GLANCE_FORECAST_TIMELINE_ITEMS) <= 10)
    }

    @Test
    fun `forecast card profile uses the real child height`() {
        assertEquals(ForecastCardHeightProfile.DENSE, forecastCardHeightProfile(60f))
        assertEquals(ForecastCardHeightProfile.COMPACT, forecastCardHeightProfile(84f))
        assertEquals(ForecastCardHeightProfile.COMFORTABLE, forecastCardHeightProfile(120f))
        assertEquals(ForecastCardHeightProfile.EXPANDED, forecastCardHeightProfile(160f))
    }

    @Test
    fun `forecast card boundaries are stable`() {
        assertEquals(ForecastCardHeightProfile.COMPACT, forecastCardHeightProfile(72f))
        assertEquals(ForecastCardHeightProfile.COMFORTABLE, forecastCardHeightProfile(102f))
        assertEquals(ForecastCardHeightProfile.EXPANDED, forecastCardHeightProfile(142f))
    }

    @Test
    fun `vertical padding follows the shared two-row height profile`() {
        assertEquals(5f, forecastContainerVerticalPaddingDp(130f))
        assertEquals(7f, forecastContainerVerticalPaddingDp(160f))
        assertEquals(9f, forecastContainerVerticalPaddingDp(190f))
        assertEquals(10f, forecastContainerVerticalPaddingDp(240f))
    }

    @Test
    fun `single row profile protects low launcher cells`() {
        assertEquals(SingleRowWidgetHeightProfile.VERY_DENSE, singleRowWidgetHeightProfile(60f))
        assertEquals(SingleRowWidgetHeightProfile.DENSE, singleRowWidgetHeightProfile(76f))
        assertEquals(SingleRowWidgetHeightProfile.REGULAR, singleRowWidgetHeightProfile(90f))
    }

    @Test
    fun `single row root padding shrinks before content does`() {
        assertEquals(4f, singleRowContainerVerticalPaddingDp(60f, WidgetLayoutKind.SMALL))
        assertEquals(6f, singleRowContainerVerticalPaddingDp(76f, WidgetLayoutKind.SMALL))
        assertEquals(8f, singleRowContainerVerticalPaddingDp(90f, WidgetLayoutKind.SMALL))
        assertEquals(4f, singleRowContainerVerticalPaddingDp(60f, WidgetLayoutKind.LARGE))
        assertEquals(12f, singleRowContainerVerticalPaddingDp(90f, WidgetLayoutKind.LARGE))
    }

    @Test
    fun `small widget hides city only when vertical budget is critical`() {
        assertTrue(!shouldShowCityInSmallWidget(180f, 60f))
        assertTrue(shouldShowCityInSmallWidget(180f, 76f))
        assertTrue(!shouldShowCityInSmallWidget(130f, 90f))
    }

    @Test
    fun `wide row adds second inline forecast only with comfortable width`() {
        assertEquals(0, inlineForecastItemCount(370f))
        assertEquals(1, inlineForecastItemCount(400f))
        assertEquals(2, inlineForecastItemCount(460f))
    }

    @Test
    fun `two row profile also considers width`() {
        assertEquals(TwoRowWidgetSizeProfile.VERY_DENSE, twoRowWidgetSizeProfile(250f, 190f))
        assertEquals(TwoRowWidgetSizeProfile.VERY_DENSE, twoRowWidgetSizeProfile(340f, 145f))
        assertEquals(TwoRowWidgetSizeProfile.COMPACT, twoRowWidgetSizeProfile(300f, 200f))
        assertEquals(TwoRowWidgetSizeProfile.REGULAR, twoRowWidgetSizeProfile(340f, 200f))
    }

    @Test
    fun `compact tall header reserves room for four text lines`() {
        assertEquals(52f, compactTallHeaderHeightBudgetDp(narrow = true))
        assertEquals(56f, compactTallHeaderHeightBudgetDp(narrow = false))
    }

    @Test
    fun `bottom card profile is based on remaining height not whole widget`() {
        val available = forecastBottomStripAvailableHeightDp(
            widgetHeightDp = 175f,
            headerHeightDp = 54f,
            sectionGapDp = 7f
        )
        val profile = forecastBottomCardHeightProfile(
            widgetHeightDp = 175f,
            headerHeightDp = 54f,
            sectionGapDp = 7f
        )

        assertTrue("Le strip bas doit être plus petit que le widget complet", available < 110f)
        assertEquals(ForecastCardHeightProfile.COMPACT, profile)
    }
    @Test
    fun `hiding current weather expands the timeline and 12h bitmap budgets`() {
        val timelineWithCurrent = forecastBottomStripAvailableHeightDp(180f, 62f, 7f)
        val timelineOnly = forecastBottomStripAvailableHeightDp(180f, 0f, 0f)
        assertTrue(timelineOnly > timelineWithCurrent)

        val bitmapWithCurrent = miniForecastChartHeightDp(
            180f, 46f, 7f, MiniForecastSizeProfile.MEDIUM_3X2
        )
        val bitmapOnly = miniForecastChartHeightDp(
            180f, 0f, 0f, MiniForecastSizeProfile.MEDIUM_3X2
        )
        assertTrue(bitmapOnly > bitmapWithCurrent)
    }

    @Test
    fun `modern forecast header is hidden when vertical space is critical`() {
        assertTrue(!shouldShowForecastPanelHeader(150f, TwoRowWidgetSizeProfile.VERY_DENSE))
        assertTrue(!shouldShowForecastPanelHeader(160f, TwoRowWidgetSizeProfile.COMPACT))
        assertTrue(shouldShowForecastPanelHeader(180f, TwoRowWidgetSizeProfile.COMPACT))
        assertTrue(shouldShowForecastPanelHeader(200f, TwoRowWidgetSizeProfile.REGULAR))
    }

    @Test
    fun `modern forecast header budget stays compact`() {
        assertEquals(0f, forecastPanelHeaderHeightDp(showHeader = false, compact = true))
        assertEquals(14f, forecastPanelHeaderHeightDp(showHeader = true, compact = true))
        assertEquals(18f, forecastPanelHeaderHeightDp(showHeader = true, compact = false))
    }

    @Test
    fun `forecast widgets reclaim attribution space but preserve My location action`() {
        val normal = forecastBottomStripAvailableHeightDp(180f, 50f, 7f)
        val withRefresh = forecastBottomStripAvailableHeightDp(
            180f, 50f, 7f, footerHeightDp = locationRefreshFooterHeightDp(true)
        )
        assertEquals(16f, normal - withRefresh)
        assertEquals(0f, locationRefreshFooterHeightDp(false))
        assertEquals(16f, locationRefreshFooterHeightDp(true))
        val noLocationHeader = compactTallHeaderLayout(180f, 7f, false, true)
        val locationHeader = compactTallHeaderLayout(
            180f, 7f, false, true, footerHeightDp = locationRefreshFooterHeightDp(true)
        )
        val availableNoLocation = forecastBottomStripAvailableHeightDp(
            180f, noLocationHeader.headerBudgetDp +
                forecastPanelHeaderHeightDp(noLocationHeader.showPanelHeader, true),
            noLocationHeader.sectionGapDp, 7f
        )
        val availableLocation = forecastBottomStripAvailableHeightDp(
            180f, locationHeader.headerBudgetDp +
                forecastPanelHeaderHeightDp(locationHeader.showPanelHeader, true),
            locationHeader.sectionGapDp, 7f, locationRefreshFooterHeightDp(true)
        )
        assertTrue("Le bouton ne doit pas etre recouvert", availableLocation > 0f)
        assertTrue("La localisation doit avoir un budget explicite", availableNoLocation > availableLocation)
    }

    @Test
    fun `pill root padding reduces card and bitmap budgets`() {
        val defaultHeight = forecastBottomStripAvailableHeightDp(160f, 54f, 7f)
        val capsuleHeight = forecastBottomStripAvailableHeightDp(
            160f, 54f, 7f, rootVerticalPaddingDp = 14f
        )
        assertTrue(capsuleHeight < defaultHeight)
        val chartDefault = miniForecastChartHeightDp(
            160f, 46f, 6f, MiniForecastSizeProfile.COMPACT_2X2
        )
        val chartPill = miniForecastChartHeightDp(
            160f, 46f, 6f, MiniForecastSizeProfile.COMPACT_2X2,
            rootVerticalPaddingDp = 14f
        )
        assertTrue(chartPill < chartDefault)
        assertTrue(chartPill > 0)
    }
    @Test
    fun `metrics stack vertically in consistent order for five hours and five days`() {
        assertEquals(
            listOf(WidgetForecastMetric.CLOUD, WidgetForecastMetric.RAIN,
                WidgetForecastMetric.SUNSHINE, WidgetForecastMetric.CONVERGENCE),
            forecastVisibleMetrics(cloud = 55, rain = 80, sunshineHours = 4.25, confidence = 72)
        )
        assertEquals(
            listOf(WidgetForecastMetric.RAIN, WidgetForecastMetric.CONVERGENCE),
            forecastVisibleMetrics(null, 0, Double.NaN, 0)
        )
        assertEquals(
            listOf(WidgetForecastMetric.CLOUD),
            forecastVisibleMetrics(100, null, -1.0, null)
        )
        assertEquals(emptyList<WidgetForecastMetric>(), forecastVisibleMetrics(null, null, 25.0, null))
    }

    @Test
    fun `compact launcher resize keeps forecast panel and measures its real budget`() {
        assertTrue(compactForecastHeader(120f))
        assertTrue(compactForecastHeader(151f))
        assertTrue(!compactForecastHeader(152f))
        val heights = (120..230 step 5).map { height ->
            forecastBottomStripAvailableHeightDp(
                widgetHeightDp = height.toFloat(),
                headerHeightDp = if (compactForecastHeader(height.toFloat())) 29f else 46f,
                sectionGapDp = if (compactForecastHeader(height.toFloat())) 2f else 7f
            )
        }
        assertTrue(heights.all { it > 40f })
        assertTrue(heights.last() > heights.first())
        assertTrue(heights.min() > 40f)
    }

    @Test
    fun `metric symbols and values grow with available card height`() {
        val metrics = forecastVisibleMetrics(65, 35, 2.0, 82)
        val compact = forecastMetricLayout(70f, ForecastCardHeightProfile.DENSE, metrics)
        val expanded = forecastMetricLayout(180f, ForecastCardHeightProfile.EXPANDED, metrics)
        assertTrue(compact.textSp < expanded.textSp)
        assertTrue(compact.iconSp < expanded.iconSp)
        assertEquals(metrics, expanded.visible)
        assertTrue(expanded.iconSp > expanded.textSp)
    }

    @Test
    fun `very short cards prioritize rain and never show cut off rows`() {
        val metrics = forecastVisibleMetrics(65, 35, 2.0, 82)
        val short = forecastMetricLayout(52f, ForecastCardHeightProfile.DENSE, metrics)
        assertTrue(short.visible.size < metrics.size)
        assertTrue(WidgetForecastMetric.RAIN in short.visible)
        assertEquals(metrics.filter { it in short.visible }, short.visible)
        val noRain = forecastMetricLayout(
            45f, ForecastCardHeightProfile.DENSE,
            listOf(WidgetForecastMetric.CLOUD, WidgetForecastMetric.SUNSHINE)
        )
        assertTrue(noRain.visible.isEmpty() || WidgetForecastMetric.CLOUD in noRain.visible)
    }

    @Test
    fun `metrics never disappear while resizing to larger height across profile thresholds`() {
        val metrics = forecastVisibleMetrics(75, 42, 4.0, 64)
        var previous = 0
        for (height in 42..245) {
            val profile = forecastCardHeightProfile(height.toFloat())
            val fitting = forecastFittingCardProfile(height.toFloat(), profile, metrics.size)
            val layout = forecastMetricLayout(height.toFloat(), fitting, metrics)
            assertTrue("height=$height", layout.visible.size >= previous)
            previous = layout.visible.size
        }
    }

    @Test
    fun `large margins delay transitions without changing normal behavior`() {
        assertEquals(152f, forecastEffectiveHeightForTransitions(152f, 7f, 7f))
        assertEquals(145f, forecastEffectiveHeightForTransitions(152f, 10.5f, 7f))
        assertTrue(compactForecastHeader(forecastEffectiveHeightForTransitions(152f, 10.5f, 7f)))
        assertTrue(!compactForecastHeader(forecastEffectiveHeightForTransitions(152f, 7f, 7f)))
        assertEquals(0f, forecastEffectiveHeightForTransitions(10f, 20f, 0f))
        for (height in 120..250) {
            val effective = forecastEffectiveHeightForTransitions(height.toFloat(), 15f, 10f)
            assertTrue(effective >= 0f)
            assertTrue(effective <= height.toFloat())
        }
    }

    @Test
    fun `compact tall state transitions never remove rain rows as height grows`() {
        val metrics = forecastVisibleMetrics(60, 42, 2.0, 70)
        for (spacingFactor in listOf(0f, 0.5f, 1f, 1.5f)) {
            for (narrow in listOf(false, true)) {
                for (showCurrent in listOf(false, true)) {
                    var previousCount = 0
                    for (height in 120..260) {
                        val base = forecastContainerVerticalPaddingDp(height.toFloat())
                        val inset = base * spacingFactor
                        val header = compactTallHeaderLayout(
                            height.toFloat(), inset, narrow, showCurrent
                        )
                        val available = forecastBottomStripAvailableHeightDp(
                            widgetHeightDp = height.toFloat(),
                            headerHeightDp = header.headerBudgetDp + forecastPanelHeaderHeightDp(
                                header.showPanelHeader, compact = true
                            ),
                            sectionGapDp = header.sectionGapDp,
                            rootVerticalPaddingDp = inset
                        )
                        val profile = forecastFittingCardProfile(
                            available, forecastCardHeightProfile(available), metrics.size
                        )
                        val visible = forecastMetricLayout(available, profile, metrics).visible
                        assertTrue(
                            "h=$height spacing=$spacingFactor narrow=$narrow current=$showCurrent",
                            visible.size >= previousCount
                        )
                        if (visible.isNotEmpty()) assertTrue(WidgetForecastMetric.RAIN in visible)
                        previousCount = visible.size
                    }
                }
            }
        }
    }

}
