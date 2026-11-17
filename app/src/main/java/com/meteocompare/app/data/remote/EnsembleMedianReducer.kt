package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Réduit une réponse de l'Ensemble API à UNE série par modèle : la médiane
 * de tous ses membres, échéance par échéance.
 *
 * ─── Pourquoi la médiane ─────────────────────────────────────────────────
 * Un ensemble génératif comme WeatherNext 2 produit chaque membre en
 * injectant du bruit : la série non suffixée renvoyée par Open-Meteo n'est
 * pas un « contrôle » non perturbé mais un tirage parmi d'autres. Afficher
 * ce seul tirage reviendrait à comparer les autres modèles à un scénario
 * arbitraire. La médiane donne au contraire la valeur centrale de
 * l'ensemble, robuste aux membres extrêmes.
 *
 * ─── Format des clés ─────────────────────────────────────────────────────
 * Open-Meteo nomme les membres `V_memberNN` (un seul modèle) ou
 * `V_memberNN_<clé modèle>` (plusieurs modèles), à côté de la série de base
 * `V` / `V_<clé modèle>`. La sortie ne garde que les clés de base, remplies
 * avec la valeur réduite ; les clés sans membres (`time`, `sunrise`,
 * `sunset`…) sont recopiées telles quelles. Le résultat a donc exactement
 * la forme d'une réponse Forecast API et passe par [BatchedForecastSplitter]
 * sans adaptation.
 *
 * ─── Réduction par type de variable ──────────────────────────────────────
 * - continues (température, pluie, vent, pression…) : médiane ;
 * - directions (`wind_direction*`) : moyenne circulaire des vecteurs
 *   unitaires — une médiane arithmétique de 350° et 10° donnerait 180° ;
 * - code météo (`weather_code*`) : valeur la plus fréquente (code le plus
 *   faible en cas d'égalité), car un code WMO est une catégorie ;
 * - pourcentages entiers (`cloud_cover*`, `relative_humidity*`,
 *   `precipitation_probability*`) : médiane arrondie à l'entier.
 *
 * Une échéance où moins de la moitié des membres a une valeur est laissée à
 * `null` plutôt que d'être représentée par une minorité de membres.
 */
object EnsembleMedianReducer {

    private val MEMBER_KEY = Regex("""^(.+)_member\d+(_.+)?$""")

    fun reduce(response: BatchedForecastResponseDto): BatchedForecastResponseDto =
        response.copy(
            hourly = response.hourly?.let(::reduceSection),
            daily = response.daily?.let(::reduceSection)
        )

    internal fun reduceSection(section: JsonObject): JsonObject {
        val membersByBaseKey = LinkedHashMap<String, MutableList<JsonElement>>()
        for ((key, value) in section) {
            val match = MEMBER_KEY.matchEntire(key) ?: continue
            val baseKey = match.groupValues[1] + match.groupValues[2]
            membersByBaseKey.getOrPut(baseKey) { mutableListOf() } += value
        }
        if (membersByBaseKey.isEmpty()) return section

        val out = LinkedHashMap<String, JsonElement>(section.size - membersByBaseKey.values.sumOf { it.size })
        for ((key, value) in section) {
            if (MEMBER_KEY.matches(key)) continue
            val members = membersByBaseKey[key]
            out[key] = if (members == null) value else reduceVariable(key, listOf(value) + members)
        }
        return JsonObject(out)
    }

    private fun reduceVariable(key: String, series: List<JsonElement>): JsonElement {
        val columns = series.map { it.asNullableDoubles() }
        val size = columns.maxOf { it.size }
        val kind = kindOf(key)
        return JsonArray(
            List(size) { index ->
                val values = columns.mapNotNull { it.getOrNull(index) }
                if (values.isEmpty() || values.size * 2 < columns.size) {
                    JsonNull
                } else {
                    when (kind) {
                        Kind.CONTINUOUS -> JsonPrimitive(median(values))
                        Kind.PERCENT -> JsonPrimitive(median(values).roundToInt())
                        Kind.DIRECTION -> circularMean(values)?.let(::JsonPrimitive) ?: JsonNull
                        Kind.CATEGORY -> JsonPrimitive(mode(values))
                    }
                }
            }
        )
    }

    private enum class Kind { CONTINUOUS, PERCENT, DIRECTION, CATEGORY }

    private fun kindOf(key: String): Kind = when {
        key.startsWith("wind_direction") -> Kind.DIRECTION
        key.startsWith("weather_code") -> Kind.CATEGORY
        key.startsWith("cloud_cover") ||
            key.startsWith("relative_humidity") ||
            key.startsWith("precipitation_probability") -> Kind.PERCENT
        else -> Kind.CONTINUOUS
    }

    internal fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    /** Direction moyenne en degrés [0, 360), ou null si les vecteurs s'annulent. */
    internal fun circularMean(degrees: List<Double>): Int? {
        var sumSin = 0.0
        var sumCos = 0.0
        for (degree in degrees) {
            val radians = Math.toRadians(degree)
            sumSin += sin(radians)
            sumCos += cos(radians)
        }
        if (hypot(sumSin, sumCos) < 1e-6 * degrees.size) return null
        val mean = Math.toDegrees(atan2(sumSin, sumCos)).roundToInt()
        return ((mean % 360) + 360) % 360
    }

    internal fun mode(values: List<Double>): Int =
        values.map(Double::roundToInt)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .first()
            .key

    private fun JsonElement.asNullableDoubles(): List<Double?> =
        (this as? JsonArray)?.map { element ->
            (element as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull
        }.orEmpty()
}
