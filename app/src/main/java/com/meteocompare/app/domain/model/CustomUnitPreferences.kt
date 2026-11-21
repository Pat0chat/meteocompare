package com.meteocompare.app.domain.model

/** The independent display dimensions available in the custom units dialog. */
interface UnitChoice { val symbol: String }

enum class TemperatureChoice(override val symbol: String) : UnitChoice { CELSIUS("°C"), FAHRENHEIT("°F") }
enum class WindChoice(override val symbol: String) : UnitChoice {
    KMH("km/h"), MPH("mph"), KNOTS("kn"), MS("m/s")
}
enum class PrecipitationChoice(override val symbol: String) : UnitChoice { MM("mm"), INCHES("in") }
enum class PressureChoice(override val symbol: String) : UnitChoice { HPA("hPa"), INHG("inHg") }
enum class HeightChoice(override val symbol: String) : UnitChoice { METRES("m"), FEET("ft") }
enum class DistanceChoice(override val symbol: String) : UnitChoice { KM("km"), MILES("mi") }

/** Display-only choices. API data, calculations and saved forecast values stay metric. */
data class CustomUnitPreferences(
    val temperature: TemperatureChoice = TemperatureChoice.CELSIUS,
    val wind: WindChoice = WindChoice.KMH,
    val precipitation: PrecipitationChoice = PrecipitationChoice.MM,
    val pressure: PressureChoice = PressureChoice.HPA,
    val height: HeightChoice = HeightChoice.METRES,
    val distance: DistanceChoice = DistanceChoice.KM
)
