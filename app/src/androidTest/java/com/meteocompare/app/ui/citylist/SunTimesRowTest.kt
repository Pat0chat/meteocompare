package com.meteocompare.app.ui.citylist

import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

/**
 * Tests d'instrumentation de [SunTimesRow].
 *
 * ─── Périmètre ────────────────────────────────────────────────────────────
 * On ne teste PAS l'exactitude du calcul astronomique (couvert par
 * [com.meteocompare.app.domain.util.SolarTimesTest]) ni la palette d'icônes.
 * On teste :
 *   1. Le rendu se fait sans crasher pour les 3 cas : normaux, partiellement
 *      null, entièrement null (nuit polaire)
 *   2. Les heures sont bien formatées HH:mm (FR)
 *   3. La sémantique a11y est fournie et lit une phrase cohérente
 *
 * ─── Sur la locale ────────────────────────────────────────────────────────
 * Locale fixée à FRENCH dans le setup pour rendre les tests déterministes —
 * sinon un CI qui tourne en EN afficherait "6:12 AM" au lieu de "06:12" et
 * ferait tomber les assertions de texte.
 */
class SunTimesRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `affiche lever et coucher en HHmm quand les deux sont fournis`() {
        Locale.setDefault(Locale.FRENCH)
        composeRule.setContent {
            SunTimesRow(
                sunrise = LocalTime.of(6, 12),
                sunset = LocalTime.of(21, 45)
            )
        }
        composeRule.onNodeWithText("06:12").assertIsDisplayed()
        composeRule.onNodeWithText("21:45").assertIsDisplayed()
    }

    @Test
    fun `fournit une description a11y consolidee pour TalkBack`() {
        Locale.setDefault(Locale.FRENCH)
        composeRule.setContent {
            SunTimesRow(
                sunrise = LocalTime.of(6, 12),
                sunset = LocalTime.of(21, 45)
            )
        }
        composeRule.onNodeWithTag(TAG_SUN_TIMES_ROW)
            .assertContentDescriptionContains("06:12", substring = true)
        composeRule.onNodeWithTag(TAG_SUN_TIMES_ROW)
            .assertContentDescriptionContains("21:45", substring = true)
    }

    @Test
    fun `affiche des tirets si sunrise et sunset sont null nuit polaire`() {
        Locale.setDefault(Locale.FRENCH)
        composeRule.setContent {
            SunTimesRow(sunrise = null, sunset = null)
        }
        composeRule.onAllNodesWithText("—").assertCountEquals(2)
    }

    @Test
    fun `affiche un tiret pour sunset seul si seulement sunrise est null`() {
        Locale.setDefault(Locale.FRENCH)
        composeRule.setContent {
            SunTimesRow(
                sunrise = null,
                sunset = LocalTime.of(18, 0)
            )
        }
        composeRule.onNodeWithText("—").assertIsDisplayed()
        composeRule.onNodeWithText("18:00").assertIsDisplayed()
    }
}
