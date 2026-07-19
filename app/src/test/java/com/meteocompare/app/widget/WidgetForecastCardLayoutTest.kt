package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetForecastCardLayoutTest {

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
        assertEquals(7f, forecastContainerVerticalPaddingDp(130f))
        assertEquals(9f, forecastContainerVerticalPaddingDp(160f))
        assertEquals(11f, forecastContainerVerticalPaddingDp(190f))
        assertEquals(13f, forecastContainerVerticalPaddingDp(240f))
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
}
