package com.meteocompare.app.notification

import android.content.Context
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.persistLocalePreference
import com.meteocompare.app.core.locale.readPersistedLocaleTag
import com.meteocompare.app.domain.model.ForecastEvolutionHighlight
import com.meteocompare.app.domain.model.ForecastEvolutionTrend
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherNotification
import com.meteocompare.app.testutil.TestFixtures
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Vérifie la copie réellement rendue par les notifications, indépendamment des canaux système. */
@RunWith(AndroidJUnit4::class)
class WeatherNotifierPresentationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var previousLanguageTag: String
    private var hadPreviousLanguageTag = false
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        val previous = readPersistedLocaleTag(context)
        hadPreviousLanguageTag = previous != null
        previousLanguageTag = previous.orEmpty()
        check(persistLocalePreference(context, "fr"))
    }

    @After
    fun tearDown() {
        check(persistLocalePreference(context, previousLanguageTag.takeIf { hadPreviousLanguageTag }))
        Locale.setDefault(originalLocale)
    }

    @Test
    fun dailySummaryIsScannableAndDetailsMetrics() {
        val rendered = WeatherNotifier(context).render(
            WeatherNotification.DailySummary(
                city = TestFixtures.paris,
                date = LocalDate.of(2026, 9, 30),
                isToday = false,
                condition = WeatherCondition.CLEAR,
                tempMin = 9.2,
                tempMax = 18.1,
                precipitationProbabilityPercent = 35,
                precipitationAmountMm = 1.4,
                convergencePercent = 82
            )
        )

        assertEquals("${TestFixtures.paris.name} · demain", rendered.title)
        assertTrue(rendered.text.contains("Ciel clair"))
        assertTrue(rendered.text.contains("9° / 18°"))
        assertTrue(rendered.text.contains("Pluie 35 %"))

        val lines = rendered.bigText.toString().lines()
        assertEquals(4, lines.size)
        assertEquals("Ciel clair", lines[0])
        assertEquals("Températures · min 9° · max 18°", lines[1])
        assertEquals("Pluie 35 % · 1,4 mm", lines[2])
        assertEquals("Accord des modèles · 82 %", lines[3])

        assertStyle(rendered.bigText, "Ciel clair", Typeface.BOLD)
        assertColor(rendered.bigText, "Ciel clair")
        assertStyle(rendered.bigText, "Températures", Typeface.BOLD)
        assertStyle(rendered.bigText, "9°", Typeface.BOLD)
        assertColor(rendered.bigText, "9°")
        assertStyle(rendered.bigText, "Pluie", Typeface.BOLD)
        assertStyle(rendered.bigText, "35", Typeface.BOLD)
        assertColor(rendered.bigText, "35")
        assertStyle(rendered.bigText, "Accord des modèles", Typeface.BOLD)
        assertStyle(rendered.bigText, "82", Typeface.BOLD)
        assertColor(rendered.bigText, "82")
    }

    @Test
    fun divergenceClearlySeparatesLowAgreementFromWeatherProbability() {
        val rendered = WeatherNotifier(context).render(
            WeatherNotification.ModelDivergence(
                city = TestFixtures.paris,
                date = LocalDate.of(2026, 9, 30),
                isToday = false,
                convergencePercent = 41
            )
        )

        assertEquals("${TestFixtures.paris.name} · prévision incertaine", rendered.title)
        assertEquals("Demain · seulement 41 % d’accord entre les modèles", rendered.text.toString())
        assertTrue(rendered.bigText.contains("Accord des modèles · 41 %"))
        assertTrue(rendered.bigText.contains("scénarios météo divergent"))
        assertFalse(rendered.bigText.contains("chance"))
        assertFalse(rendered.bigText.contains("probabilité"))
        assertStyle(rendered.text, "Demain", Typeface.BOLD)
        assertStyle(rendered.text, "41", Typeface.BOLD)
        assertColor(rendered.text, "41")
        assertStyle(
            rendered.bigText,
            "Les scénarios météo divergent nettement. Les valeurs peuvent encore évoluer.",
            Typeface.ITALIC
        )
    }

    @Test
    fun takeawayShowsDateAmplitudeConsensusAndRevisionAge() {
        val rendered = WeatherNotifier(context).render(
            WeatherNotification.ForecastChange(
                city = TestFixtures.paris,
                highlight = ForecastEvolutionHighlight(
                    targetDate = LocalDate.of(2026, 9, 30),
                    variable = ForecastEvolutionVariable.TEMPERATURE,
                    trend = ForecastEvolutionTrend.INCREASING,
                    medianDelta = 2.4,
                    comparedModels = 4,
                    dominantModels = 3,
                    previousAgeHours = 24
                )
            )
        )

        assertEquals("${TestFixtures.paris.name} · À retenir", rendered.title)
        assertTrue(rendered.text.contains("Température max +2,4 °C"))

        val lines = rendered.bigText.toString().lines()
        assertEquals(4, lines.size)
        assertTrue(lines[0].contains("30"))
        assertEquals("La température prévue augmente · +2,4 °C", lines[1])
        assertEquals("3 modèles sur 4 confirment cette évolution", lines[2])
        assertEquals("Comparé à la prévision d’il y a environ 24 h", lines[3])
        assertFalse(rendered.bigText.contains("H−"))
        assertStyle(rendered.text, "Température max", Typeface.BOLD)
        assertStyle(rendered.text, "+2,4 °C", Typeface.BOLD)
        assertColor(rendered.text, "+2,4 °C")
        assertStyle(rendered.bigText, "La température prévue augmente", Typeface.BOLD)
        assertStyle(rendered.bigText, "+2,4 °C", Typeface.BOLD)
        assertColor(rendered.bigText, "+2,4 °C")
        assertStyle(
            rendered.bigText,
            "Comparé à la prévision d’il y a environ 24 h",
            Typeface.ITALIC
        )
    }

    @Test
    fun rainAndWindRevisionsUseNaturalUnits() {
        val notifier = WeatherNotifier(context)
        val rain = notifier.render(
            WeatherNotification.ForecastChange(
                city = TestFixtures.paris,
                highlight = ForecastEvolutionHighlight(
                    targetDate = LocalDate.of(2026, 10, 1),
                    variable = ForecastEvolutionVariable.PRECIPITATION,
                    trend = ForecastEvolutionTrend.DECREASING,
                    medianDelta = -3.5,
                    comparedModels = 4,
                    dominantModels = 3,
                    previousAgeHours = 25
                )
            )
        )
        val wind = notifier.render(
            WeatherNotification.ForecastChange(
                city = TestFixtures.paris,
                highlight = ForecastEvolutionHighlight(
                    targetDate = LocalDate.of(2026, 10, 1),
                    variable = ForecastEvolutionVariable.WIND,
                    trend = ForecastEvolutionTrend.DECREASING,
                    medianDelta = -11.6,
                    comparedModels = 4,
                    dominantModels = 4,
                    previousAgeHours = 25
                )
            )
        )

        assertTrue(rain.text.contains("Pluie prévue −3,5 mm"))
        assertTrue(rain.bigText.contains("Le scénario pluie s’atténue · −3,5 mm"))
        assertTrue(wind.text.contains("Vent max −12 km/h"))
        assertTrue(wind.bigText.contains("Le vent prévu s’atténue · −12 km/h"))
    }

    @Test
    fun takeawayIsLocalizedInAllSupportedLanguages() {
        val notification = WeatherNotification.ForecastChange(
            city = TestFixtures.paris,
            highlight = ForecastEvolutionHighlight(
                targetDate = LocalDate.of(2026, 10, 1),
                variable = ForecastEvolutionVariable.WIND,
                trend = ForecastEvolutionTrend.INCREASING,
                medianDelta = 9.0,
                comparedModels = 4,
                dominantModels = 3,
                previousAgeHours = 24
            )
        )
        val expectedTitles = mapOf(
            "fr" to "${TestFixtures.paris.name} · À retenir",
            "en" to "${TestFixtures.paris.name} · Key point",
            "de" to "${TestFixtures.paris.name} · Wichtige Punkte",
            "es" to "${TestFixtures.paris.name} · Puntos clave",
            "it" to "${TestFixtures.paris.name} · Da ricordare"
        )

        expectedTitles.forEach { (languageTag, expectedTitle) ->
            check(persistLocalePreference(context, languageTag))
            assertEquals(expectedTitle, WeatherNotifier(context).render(notification).title)
        }
    }

    @Test
    fun volatileRevisionDoesNotShowMisleadingMedianDelta() {
        val rendered = WeatherNotifier(context).render(
            WeatherNotification.ForecastChange(
                city = TestFixtures.paris,
                highlight = ForecastEvolutionHighlight(
                    targetDate = LocalDate.of(2026, 10, 1),
                    variable = ForecastEvolutionVariable.PRECIPITATION,
                    trend = ForecastEvolutionTrend.VOLATILE,
                    medianDelta = 8.7,
                    comparedModels = 5,
                    dominantModels = 2,
                    previousAgeHours = 27
                )
            )
        )

        assertTrue(rendered.text.contains("Pluie prévue · révision incertaine"))
        assertTrue(rendered.bigText.contains("Pluie prévue · les modèles révisent dans des sens différents"))
        assertTrue(rendered.bigText.contains("5 modèles comparés · pas de tendance dominante"))
        assertFalse(rendered.text.contains("8,7"))
        assertFalse(rendered.bigText.contains("8,7"))
        assertStyle(rendered.text, "révision incertaine", Typeface.BOLD)
        assertColor(rendered.text, "révision incertaine")
        assertStyle(
            rendered.bigText,
            "les modèles révisent dans des sens différents",
            Typeface.ITALIC
        )
        assertColor(rendered.bigText, "les modèles révisent dans des sens différents")
        assertStyle(
            rendered.bigText,
            "5 modèles comparés · pas de tendance dominante",
            Typeface.ITALIC
        )
    }

    @Test
    fun dailyRemoteViewsActuallyRenderBoldAndColors() {
        val notification = WeatherNotification.DailySummary(
            city = TestFixtures.paris,
            date = LocalDate.of(2026, 9, 30),
            isToday = false,
            condition = WeatherCondition.CLEAR,
            tempMin = 9.2,
            tempMax = 18.1,
            precipitationProbabilityPercent = 35,
            precipitationAmountMm = 1.4,
            convergencePercent = 82
        )
        val built = WeatherNotifier(context).buildForTest(notification)
        val compact = built.compactView.apply(context, FrameLayout(context))
        val expanded = built.expandedView.apply(context, FrameLayout(context))

        val compactTitle = compact.findViewById<TextView>(R.id.notification_custom_title)
        val compactCondition = compact.findViewById<TextView>(R.id.notification_compact_primary)
        val compactTemperature = compact.findViewById<TextView>(R.id.notification_compact_secondary)
        assertTrue(compactTitle.typeface.isBold)
        assertTrue(compactCondition.typeface.isBold)
        assertTrue(compactTemperature.typeface.isBold)
        assertEquals(
            ContextCompat.getColor(context, R.color.notification_text_temperature),
            compactTemperature.currentTextColor
        )

        val hero = expanded.findViewById<TextView>(R.id.notification_custom_hero)
        val tempValue = expanded.findViewById<TextView>(R.id.notification_custom_row_1_value)
        val rainValue = expanded.findViewById<TextView>(R.id.notification_custom_row_2_value)
        val agreementValue = expanded.findViewById<TextView>(R.id.notification_custom_row_3_value)
        assertEquals("Ciel clair", hero.text.toString())
        assertTrue(hero.typeface.isBold)
        assertTrue(tempValue.typeface.isBold)
        assertEquals(
            ContextCompat.getColor(context, R.color.notification_text_temperature),
            tempValue.currentTextColor
        )
        assertEquals(
            ContextCompat.getColor(context, R.color.notification_text_precipitation),
            rainValue.currentTextColor
        )
        assertEquals(View.VISIBLE, agreementValue.visibility)
    }

    @Test
    fun divergenceRemoteViewsRenderRedAlertAndItalicExplanation() {
        val built = WeatherNotifier(context).buildForTest(
            WeatherNotification.ModelDivergence(
                city = TestFixtures.paris,
                date = LocalDate.of(2026, 9, 30),
                isToday = false,
                convergencePercent = 41
            )
        )
        val expanded = built.expandedView.apply(context, FrameLayout(context))
        val agreement = expanded.findViewById<TextView>(R.id.notification_custom_row_1_value)
        val detail = expanded.findViewById<TextView>(R.id.notification_custom_detail)

        assertTrue(agreement.typeface.isBold)
        assertEquals(
            ContextCompat.getColor(context, R.color.notification_text_low_confidence),
            agreement.currentTextColor
        )
        assertTrue(detail.typeface.isItalic)
        assertTrue(detail.text.toString().contains("scénarios météo divergent"))
    }

    @Test
    fun takeawayRemoteViewsEmphasizeDeltaAndItalicizeReference() {
        val built = WeatherNotifier(context).buildForTest(
            WeatherNotification.ForecastChange(
                city = TestFixtures.paris,
                highlight = ForecastEvolutionHighlight(
                    targetDate = LocalDate.of(2026, 9, 30),
                    variable = ForecastEvolutionVariable.TEMPERATURE,
                    trend = ForecastEvolutionTrend.INCREASING,
                    medianDelta = 2.4,
                    comparedModels = 4,
                    dominantModels = 3,
                    previousAgeHours = 24
                )
            )
        )
        val compact = built.compactView.apply(context, FrameLayout(context))
        val expanded = built.expandedView.apply(context, FrameLayout(context))
        val compactDelta = compact.findViewById<TextView>(R.id.notification_compact_secondary)
        val expandedDelta = expanded.findViewById<TextView>(R.id.notification_custom_row_1_value)
        val detail = expanded.findViewById<TextView>(R.id.notification_custom_detail)

        assertEquals("+2,4 °C", compactDelta.text.toString())
        assertTrue(compactDelta.typeface.isBold)
        assertEquals(
            ContextCompat.getColor(context, R.color.notification_text_temperature),
            expandedDelta.currentTextColor
        )
        assertTrue(detail.typeface.isItalic)
        assertTrue(detail.text.toString().contains("24 h"))
    }

    private fun assertStyle(text: CharSequence, token: String, style: Int) {
        val spanned = text as? Spanned
            ?: error("Le texte '$text' devrait conserver des spans Android")
        val start = text.toString().indexOf(token)
        assertTrue("Token '$token' introuvable dans '$text'", start >= 0)
        val spans = spanned.getSpans(start, start + token.length, StyleSpan::class.java)
        assertTrue(
            "Le token '$token' devrait avoir le style $style dans '$text'",
            spans.any { it.style == style || (style == Typeface.BOLD && it.style == Typeface.BOLD_ITALIC) ||
                (style == Typeface.ITALIC && it.style == Typeface.BOLD_ITALIC) }
        )
    }

    private fun assertColor(text: CharSequence, token: String) {
        val spanned = text as? Spanned
            ?: error("Le texte '$text' devrait conserver des spans Android")
        val start = text.toString().indexOf(token)
        assertTrue("Token '$token' introuvable dans '$text'", start >= 0)
        assertTrue(
            "Le token '$token' devrait porter une couleur sémantique dans '$text'",
            spanned.getSpans(start, start + token.length, ForegroundColorSpan::class.java).isNotEmpty()
        )
    }
}
