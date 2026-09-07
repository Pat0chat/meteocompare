package com.meteocompare.app.ui.components

import com.meteocompare.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppToastEventTest {

    @Test
    fun `success utilise le style positif et une duree courte`() {
        val event = AppToastEvent.success(R.string.toast_city_added, "Paris")

        assertEquals(AppToastType.SUCCESS, event.type)
        assertEquals(AppToastDuration.SHORT, event.duration)
        assertEquals(listOf("Paris"), event.formatArgs)
        assertNull(event.actionLabelRes)
    }

    @Test
    fun `error utilise une duree longue`() {
        val event = AppToastEvent.error(R.string.refresh_error, "Timeout")

        assertEquals(AppToastType.ERROR, event.type)
        assertEquals(AppToastDuration.LONG, event.duration)
        assertEquals(listOf("Timeout"), event.formatArgs)
    }

    @Test
    fun `warning et info conservent des niveaux distincts`() {
        val warning = AppToastEvent.warning(R.string.toast_refresh_all_partial)
        val info = AppToastEvent.info(R.string.settings_bias_refresh_queued)

        assertEquals(AppToastType.WARNING, warning.type)
        assertEquals(AppToastDuration.LONG, warning.duration)
        assertEquals(AppToastType.INFO, info.type)
        assertEquals(AppToastDuration.SHORT, info.duration)
    }
}
