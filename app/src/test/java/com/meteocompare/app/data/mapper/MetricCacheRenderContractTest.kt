package com.meteocompare.app.data.mapper

import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.model.WeatherModel
import java.util.Locale
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real cached Open-Meteo JSON -> mapper -> rendering, with no network available or needed. */
class MetricCacheRenderContractTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `offline cache stays metric and byte identical across repeated unit switches`() {
        val payload = """{
          "latitude":48.85,"longitude":2.35,"timezone":"UTC",
          "hourly":{
            "time":["2026-09-16T00:00","2026-09-16T01:00","2026-09-16T02:00","2026-09-16T03:00"],
            "temperature_2m":[-40.0,0.0,20.0,null],
            "precipitation":[0.0,0.05,25.4,null],
            "wind_speed_10m":[0.0,16.09344,32.18688,null],
            "wind_gusts_10m":[0.0,32.18688,64.37376,null]
          }
        }""".trimIndent()
        val file = folder.newFile("cached-forecast.json").apply { writeText(payload) }
        val beforeBytes = file.readBytes()
        val json = Json { ignoreUnknownKeys = true }
        val dto = json.decodeFromString<ForecastResponseDto>(file.readText())
        val mapper = ForecastMapper()
        val canonical = mapper.toSeries(WeatherModel.GFS, dto)
        for (system in listOf(UnitSystem.METRIC, UnitSystem.IMPERIAL, UnitSystem.METRIC)) {
            val units = WeatherUnits(system)
            val series = mapper.toSeries(WeatherModel.GFS, json.decodeFromString(file.readText()))
            val h = series.hourly
            assertEquals(canonical, series)
            assertEquals(20.0, h.temperature2m[2]!!, 0.0)
            assertEquals(0.05, h.precipitation[1]!!, 0.0)
            assertEquals(32.18688, h.windGusts10m[1]!!, 0.0)
            assertEquals(4, h.timestamps.size)
            assertEquals("—", units.temp(h.temperature2m[3], locale = Locale.US))
            if (system == UnitSystem.IMPERIAL) {
                assertEquals("68°F", units.temp(h.temperature2m[2], locale = Locale.US))
                assertEquals("<0.01 in", units.rain(h.precipitation[1], Locale.US))
                assertEquals("10 mph", units.speed(h.windSpeed10m[1], Locale.US))
                assertEquals("20 mph", units.speed(h.windGusts10m[1], Locale.US))
            } else {
                assertEquals("20°", units.temp(h.temperature2m[2], locale = Locale.US))
                assertEquals("16 km/h", units.speed(h.windSpeed10m[1], Locale.US))
            }
            assertArrayEquals(beforeBytes, file.readBytes())
            assertEquals(dto, json.decodeFromString<ForecastResponseDto>(json.encodeToString(ForecastResponseDto.serializer(), dto)))
        }
    }
}
