package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherConditionConsensusTest {

    @Test
    fun `ciel sec ne fragmente plus le vote entre quatre libelles`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.AROME_FRANCE_HD, WeatherCondition.OVERCAST),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, WeatherCondition.OVERCAST),
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.OVERCAST),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.PARTLY_CLOUDY),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, WeatherCondition.PARTLY_CLOUDY),
            ForecastConsensus.Entry(WeatherModel.GEM_GLOBAL, WeatherCondition.MAINLY_CLEAR),
            ForecastConsensus.Entry(WeatherModel.METNO_NORDIC, WeatherCondition.MAINLY_CLEAR),
            ForecastConsensus.Entry(WeatherModel.KNMI_HARMONIE_EU, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.BOM_ACCESS, WeatherCondition.CLEAR)
        )

        val result = WeatherConditionConsensus.resolve(entries, cloudCoverPercent = 62.0)

        assertEquals(WeatherCondition.PARTLY_CLOUDY, result.value)
        // La convergence reste le vote brut : OVERCAST est seulement 3/9.
        assertEquals(33, result.percent)
    }

    @Test
    fun `phenomene significatif majoritaire reste prioritaire sur la nebulosite`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ICON_GLOBAL, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, WeatherCondition.MAINLY_CLEAR)
        )

        val result = WeatherConditionConsensus.resolve(entries, cloudCoverPercent = 10.0)

        assertEquals(WeatherCondition.RAIN, result.value)
    }

    @Test
    fun `egalite ciel sec pluie reste prudente et choisit pluie`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.RAIN)
        )

        val result = WeatherConditionConsensus.resolve(entries, cloudCoverPercent = 5.0)

        assertEquals(WeatherCondition.RAIN, result.value)
    }

    @Test
    fun `nebulosite seule peut resoudre un ciel sec sans code WMO`() {
        val result = WeatherConditionConsensus.resolve(emptyList(), cloudCoverPercent = 82.0)

        assertEquals(WeatherCondition.PARTLY_CLOUDY, result.value)
        assertEquals(null, result.percent)
    }

    @Test
    fun `les precipitations liquides ne sont plus fragmentees entre bruine averses et pluie`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.AROME_FRANCE_HD, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, WeatherCondition.MAINLY_CLEAR),
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.PARTLY_CLOUDY),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.OVERCAST),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, WeatherCondition.DRIZZLE),
            ForecastConsensus.Entry(WeatherModel.GEM_GLOBAL, WeatherCondition.DRIZZLE),
            ForecastConsensus.Entry(WeatherModel.METNO_NORDIC, WeatherCondition.RAIN_SHOWERS),
            ForecastConsensus.Entry(WeatherModel.KNMI_HARMONIE_EU, WeatherCondition.RAIN_SHOWERS),
            ForecastConsensus.Entry(WeatherModel.BOM_ACCESS, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.CMA_GRAPES, WeatherCondition.RAIN)
        )

        val result = WeatherConditionConsensus.resolve(entries, cloudCoverPercent = 35.0)

        // 6 familles prévoient une précipitation liquide contre 4 sans précipitation.
        // À l'intérieur de LIQUID, l'égalité 2/2/2 est départagée prudemment vers RAIN.
        assertEquals(WeatherCondition.RAIN, result.value)
        // La convergence reste brute : aucune feuille exacte ne dépasse 2/10.
        assertEquals(20, result.percent)
    }

    @Test
    fun `neige et averses de neige se consolident avant comparaison a la pluie`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.AROME_FRANCE_HD, WeatherCondition.SNOW),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, WeatherCondition.SNOW),
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.SNOW_SHOWERS),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.SNOW_SHOWERS),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.GEM_GLOBAL, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.METNO_NORDIC, WeatherCondition.RAIN)
        )

        val result = WeatherConditionConsensus.resolve(entries, cloudCoverPercent = 100.0)

        // PRECIPITATION gagne au premier niveau, puis FROZEN 4 voix contre LIQUID 3.
        // L'égalité SNOW/SNOW_SHOWERS est tranchée vers SNOW.
        assertEquals(WeatherCondition.SNOW, result.value)
        assertEquals(43, result.percent) // RAIN reste la feuille brute la plus fréquente : 3/7.
    }

    @Test
    fun `non precipitation consolide ciel et brouillard avant comparaison a la pluie`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.AROME_FRANCE_HD, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, WeatherCondition.PARTLY_CLOUDY),
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.FOG),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.FOG),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.GEM_GLOBAL, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.METNO_NORDIC, WeatherCondition.RAIN)
        )

        val result = WeatherConditionConsensus.resolve(entries, cloudCoverPercent = 55.0)

        // NON_PRECIPITATION gagne 4/7 ; à l'intérieur, SKY et FOG sont à égalité,
        // donc le tie-break prudent retient FOG.
        assertEquals(WeatherCondition.FOG, result.value)
        assertEquals(43, result.percent) // RAIN est la feuille brute dominante 3/7.
    }

    @Test
    fun `orage ne gagne pas seulement parce qu il est plus severe si pluie liquide est majoritaire`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.AROME_FRANCE_HD, WeatherCondition.THUNDERSTORM),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, WeatherCondition.THUNDERSTORM),
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.GEM_GLOBAL, WeatherCondition.CLEAR)
        )

        val result = WeatherConditionConsensus.resolve(entries, cloudCoverPercent = 10.0)

        assertEquals(WeatherCondition.RAIN, result.value)
    }

    @Test
    fun `chaque condition connue reste une feuille valide de la hierarchie`() {
        val models = WeatherModel.entries
        WeatherCondition.entries.filter { it != WeatherCondition.UNKNOWN }.forEachIndexed { index, condition ->
            val result = WeatherConditionConsensus.resolve(
                entries = listOf(ForecastConsensus.Entry(models[index % models.size], condition)),
                cloudCoverPercent = null
            )
            assertEquals(condition, result.value)
        }
    }

    @Test
    fun `la hierarchie conserve l equilibrage par lignee a chaque niveau`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.ICON_D2, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ICON_EU, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ICON_GLOBAL, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.MAINLY_CLEAR)
        )

        val result = WeatherConditionConsensus.resolve(entries, cloudCoverPercent = 12.0)

        // Les trois ICON ne comptent que pour une lignée. NON_PRECIPITATION a donc
        // deux lignées (GFS + ECMWF) contre une seule lignée ICON pour RAIN.
        assertEquals(WeatherCondition.CLEAR, result.value)
        assertEquals(33, result.percent)
        assertEquals(3, result.familyCount)
    }


    @Test
    fun `unknown est ignore et la nebulosite sert seulement de fallback`() {
        val unknownOnly = listOf(
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.UNKNOWN),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.UNKNOWN)
        )

        val withCloud = WeatherConditionConsensus.resolve(unknownOnly, cloudCoverPercent = 90.0)
        val withoutCloud = WeatherConditionConsensus.resolve(unknownOnly, cloudCoverPercent = null)

        assertEquals(WeatherCondition.OVERCAST, withCloud.value)
        assertEquals(null, withCloud.percent)
        assertEquals(null, withoutCloud.value)
        assertEquals(null, withoutCloud.percent)
    }

    @Test
    fun `le consensus hierarchique est invariant a l ordre des modeles`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.AROME_FRANCE_HD, WeatherCondition.DRIZZLE),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, WeatherCondition.RAIN_SHOWERS),
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.SNOW_SHOWERS),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.GEM_GLOBAL, WeatherCondition.FOG)
        )

        val baseline = WeatherConditionConsensus.resolve(entries, cloudCoverPercent = 67.0)
        val reversed = WeatherConditionConsensus.resolve(entries.reversed(), cloudCoverPercent = 67.0)
        val rotated = WeatherConditionConsensus.resolve(entries.drop(2) + entries.take(2), cloudCoverPercent = 67.0)

        assertEquals(baseline, reversed)
        assertEquals(baseline, rotated)
    }

    @Test
    fun `la convergence brute ne change pas quand la nebulosite change la feuille sky`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.MAINLY_CLEAR),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, WeatherCondition.PARTLY_CLOUDY),
            ForecastConsensus.Entry(WeatherModel.GEM_GLOBAL, WeatherCondition.OVERCAST)
        )

        val openSky = WeatherConditionConsensus.resolve(entries, cloudCoverPercent = 10.0)
        val coveredSky = WeatherConditionConsensus.resolve(entries, cloudCoverPercent = 95.0)

        assertEquals(WeatherCondition.CLEAR, openSky.value)
        assertEquals(WeatherCondition.OVERCAST, coveredSky.value)
        assertEquals(openSky.percent, coveredSky.percent)
        assertEquals(25, openSky.percent)
    }
}
