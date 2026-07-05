package com.meteocompare.app.widget

import com.meteocompare.app.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires purs des helpers de formatage widget.
 *
 * Ces helpers sont extraits de [MeteoWidget] justement pour être testables
 * sans dépendance Glance/Android — un JVM test standard suffit.
 *
 * Locale : buildExtrasLine utilise "%.1f".format(...) qui dépend de la
 * locale par défaut. Sur une machine française, le décimal est une VIRGULE
 * ("1,2 mm") ; sur une machine anglaise, un POINT ("1.2 mm"). Les tests
 * qui vérifient la présence exacte du format numérique se contentent de
 * vérifier les substrings non-décimales (émoji, unité) pour rester agnostiques
 * à la locale de la CI.
 */
class WidgetFormattingTest {

    // ─── buildExtrasLine ───────────────────────────────────────────────

    @Test
    fun `buildExtrasLine - vide quand aucun extra disponible`() {
        val data = WidgetData.empty(
            cityName = "Paris",
            error = WidgetError.Loading
        )
        assertEquals("", buildExtrasLine(data))
    }

    @Test
    fun `buildExtrasLine - vent seul quand disponible et condition claire`() {
        // Ciel clair → pas de cloud badge (règle métier).
        // Pas de pluie prévue → pas de precip badge.
        // Seul le vent s'affiche.
        val data = WidgetData.empty(cityName = "Paris", error = WidgetError.Loading)
            .copy(
                currentCondition = WeatherCondition.CLEAR,
                currentWindSpeedKmh = 12.4
            )
        assertEquals("💨 12 km/h", buildExtrasLine(data))
    }

    @Test
    fun `buildExtrasLine - vent arrondi à l'entier`() {
        // 14.7 → 15 (arrondi standard), 14.3 → 14
        val data17 = WidgetData.empty(cityName = "P", error = WidgetError.Loading)
            .copy(currentCondition = WeatherCondition.CLEAR, currentWindSpeedKmh = 14.7)
        assertEquals("💨 15 km/h", buildExtrasLine(data17))

        val data13 = data17.copy(currentWindSpeedKmh = 14.3)
        assertEquals("💨 14 km/h", buildExtrasLine(data13))
    }

    @Test
    fun `buildExtrasLine - vent nul affiché (contrairement aux nuages)`() {
        // Régression contre "cacher si == 0". Un jour calme est un signal utile.
        val data = WidgetData.empty(cityName = "P", error = WidgetError.Loading)
            .copy(currentCondition = WeatherCondition.CLEAR, currentWindSpeedKmh = 0.0)
        assertEquals("💨 0 km/h", buildExtrasLine(data))
    }

    @Test
    fun `buildExtrasLine - cloud cover uniquement si PARTLY_CLOUDY ou OVERCAST`() {
        // CLEAR : pas de badge nuage même si cloud_cover valide (redondant avec ☀)
        val clear = WidgetData.empty(cityName = "P", error = WidgetError.Loading)
            .copy(currentCondition = WeatherCondition.CLEAR, currentCloudCover = 15)
        assertEquals("", buildExtrasLine(clear))

        // PARTLY_CLOUDY : badge affiché
        val partly = clear.copy(currentCondition = WeatherCondition.PARTLY_CLOUDY)
        assertEquals("☁ 15%", buildExtrasLine(partly))

        // OVERCAST : badge affiché
        val overcast = clear.copy(currentCondition = WeatherCondition.OVERCAST)
        assertEquals("☁ 15%", buildExtrasLine(overcast))

        // RAIN : pas de badge nuage (couverture implicite)
        val rain = clear.copy(currentCondition = WeatherCondition.RAIN)
        assertEquals("", buildExtrasLine(rain))
    }

    @Test
    fun `buildExtrasLine - ordre nuages puis vent puis pluie`() {
        // Ordre visuel : sky → air → water. Test que le pipeline concatène
        // dans ce sens et pas dans un autre.
        val data = WidgetData.empty(cityName = "P", error = WidgetError.Loading)
            .copy(
                currentCondition = WeatherCondition.OVERCAST,
                currentCloudCover = 80,
                currentWindSpeedKmh = 20.0,
                precipMm = 2.5,
                precipConfidencePct = 78
            )
        val extras = buildExtrasLine(data)

        // On vérifie l'ordre relatif via indexOf plutôt qu'égalité stricte,
        // pour rester tolérant à la locale sur "2.5 mm" vs "2,5 mm".
        val cloudPos = extras.indexOf("☁")
        val windPos = extras.indexOf("💨")
        val rainPos = extras.indexOf("🌧")
        assert(cloudPos in 0 until windPos) { "cloud avant vent, actual: $extras" }
        assert(windPos in 0 until rainPos) { "vent avant pluie, actual: $extras" }
        assert(extras.contains("(78%)")) { "confiance affichée, actual: $extras" }
    }

    @Test
    fun `buildExtrasLine - précipitations sans confiance omet les parenthèses`() {
        val data = WidgetData.empty(cityName = "P", error = WidgetError.Loading)
            .copy(
                currentCondition = WeatherCondition.RAIN,
                precipMm = 1.0,
                precipConfidencePct = null
            )
        val extras = buildExtrasLine(data)
        assert(extras.contains("🌧")) { "pluie affichée, actual: $extras" }
        assert(extras.contains("mm")) { "unité mm présente, actual: $extras" }
        assert(!extras.contains("(")) { "pas de parenthèses sans confidence, actual: $extras" }
    }

    // ─── formatTemp ───────────────────────────────────────────────────

    @Test
    fun `formatTemp - null renvoie tiret cadratin`() {
        assertEquals("—", formatTemp(null))
    }

    @Test
    fun `formatTemp - arrondi à l'entier avec degré`() {
        assertEquals("22°", formatTemp(22.0))
        assertEquals("23°", formatTemp(22.7)) // arrondi supérieur
        assertEquals("22°", formatTemp(22.3)) // arrondi inférieur
        assertEquals("0°", formatTemp(0.0))
        assertEquals("-5°", formatTemp(-5.4))
    }

    // ─── formatMinMax ─────────────────────────────────────────────────

    @Test
    fun `formatMinMax - les deux disponibles - format min sur max`() {
        assertEquals("12° / 22°", formatMinMax(min = 12.0, max = 22.0))
    }

    @Test
    fun `formatMinMax - max seul avec préfixe`() {
        assertEquals("max 22°", formatMinMax(min = null, max = 22.0))
    }

    @Test
    fun `formatMinMax - min seul avec préfixe`() {
        assertEquals("min 12°", formatMinMax(min = 12.0, max = null))
    }

    @Test
    fun `formatMinMax - les deux null renvoie chaîne vide`() {
        // Le layout appelle .isNotEmpty() avant d'afficher — chaîne vide est
        // le signal pour cacher toute la ligne.
        assertEquals("", formatMinMax(min = null, max = null))
    }
}
