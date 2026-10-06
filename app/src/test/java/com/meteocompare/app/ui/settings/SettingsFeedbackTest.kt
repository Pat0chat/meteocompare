package com.meteocompare.app.ui.settings

import com.meteocompare.app.R
import com.meteocompare.app.ui.components.AppToastType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsFeedbackTest {
    @Test
    fun `sauvegarde des modeles produit une confirmation`() {
        val event = modelSelectionCommitFeedback(ModelSelectionCommitResult.SAVED)

        assertEquals(R.string.toast_models_updated, event?.messageRes)
        assertEquals(AppToastType.SUCCESS, event?.type)
    }

    @Test
    fun `sauvegarde des modeles avec widget differe produit un avertissement`() {
        val event = modelSelectionCommitFeedback(
            ModelSelectionCommitResult.SAVED_WIDGET_REFRESH_DELAYED
        )

        assertEquals(R.string.toast_widget_refresh_delayed, event?.messageRes)
        assertEquals(AppToastType.WARNING, event?.type)
    }

    @Test
    fun `aucun changement ou echec ne produit pas une fausse confirmation`() {
        assertNull(modelSelectionCommitFeedback(ModelSelectionCommitResult.UNCHANGED))
        assertNull(modelSelectionCommitFeedback(ModelSelectionCommitResult.FAILED))
    }
}
