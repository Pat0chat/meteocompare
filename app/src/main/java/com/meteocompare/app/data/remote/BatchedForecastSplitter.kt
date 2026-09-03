package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import com.meteocompare.app.data.remote.dto.DailyDto
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.data.remote.dto.HourlyDto
import com.meteocompare.app.domain.model.ForecastPhysicalLimits
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Décompose une réponse batched multi-modèles en un [ForecastResponseDto]
 * par modèle demandé.
 *
 * ─── Contrat ─────────────────────────────────────────────────────────────
 * La sortie est indexée par [WeatherModel] et compatible pixel pour pixel
 * avec ce que produirait un appel individuel `?models=<one>`. Le
 * [com.meteocompare.app.data.mapper.ForecastMapper] existant peut être
 * utilisé sans modification. Le cache Room (par modèle) reste identique.
 *
 * ─── Résolution des clés ─────────────────────────────────────────────────
 * Pour chaque variable V (`temperature_2m`, `precipitation`, `weather_code`,
 * etc.) et chaque modèle M avec apiKey `k`, la lookup ordre est :
 *
 *   1. `V_k`               → suffixé (mode multi-modèles normal)
 *   2. `V` si N == 1       → non-suffixé (mode single-modèle historique)
 *
 * Sinon la variable est considérée absente pour ce modèle → liste vide dans
 * le DTO reconstruit, ce que le mapper interprète comme "variable absente
 * pour cette série" (ancien cache, horizon/zone limitée ou champ omis).
 *
 * ─── Détection d'échec par modèle ────────────────────────────────────────
 * Un modèle qui n'a répondu à AUCUNE température alignée sur une échéance
 * horaire ou journalière (typiquement hors de sa zone de couverture) est
 * considéré inexploitable. Voir [hasNoUsableData].
 *
 * ─── Robustesse ───────────────────────────────────────────────────────────
 * On force la lecture des tableaux à travers [asNullableDoubles] /
 * [asNullableInts] : ces helpers acceptent explicitement `JsonNull` comme
 * élément (Open-Meteo l'utilise pour les heures sans donnée) et ignorent
 * silencieusement les éléments d'un type inattendu (défense en profondeur).
 */
object BatchedForecastSplitter {

    /**
     * Décompose [batched] en un DTO unitaire par modèle. Les modèles pour
     * lesquels aucune donnée exploitable n'a été retournée (voir
     * [hasNoUsableData]) sont EXCLUS du résultat — l'appelant sait ainsi
     * distinguer "modèle absent = échec côté Open-Meteo" de "modèle présent
     * mais avec variables manquantes".
     */
    fun split(
        batched: BatchedForecastResponseDto,
        requestedModels: List<WeatherModel>
    ): Map<WeatherModel, ForecastResponseDto> {
        val singleModelMode = requestedModels.size == 1
        val timeHourly = batched.hourly?.get(KEY_TIME)?.asStringList().orEmpty()
        val timeDaily = batched.daily?.get(KEY_TIME)?.asStringList().orEmpty()

        val out = LinkedHashMap<WeatherModel, ForecastResponseDto>(requestedModels.size)
        for (model in requestedModels) {
            val hourly = buildHourlyDto(
                json = batched.hourly,
                model = model,
                singleModelMode = singleModelMode,
                sharedTime = timeHourly
            )
            val daily = buildDailyDto(
                json = batched.daily,
                model = model,
                singleModelMode = singleModelMode,
                sharedTime = timeDaily
            )
            val dto = ForecastResponseDto(
                latitude = batched.latitude,
                longitude = batched.longitude,
                timezone = batched.timezone,
                hourly = hourly,
                daily = daily
            )
            if (!hasNoUsableData(dto)) {
                out[model] = dto
            }
        }
        return out
    }

    /**
     * Vrai si la série retournée pour ce modèle est totalement inexploitable.
     * Une réponse daily valide reste utile même si l'API omet exceptionnellement
     * l'horaire ; on ne filtre donc le modèle que si aucune température horaire
     * ou journalière n'est disponible avec une échéance alignée.
     */
    private fun hasNoUsableData(dto: ForecastResponseDto): Boolean {
        val hasHourlyTemperature = hasAlignedValue(
            times = dto.hourly?.time,
            values = dto.hourly?.temperature2m
        )
        val hasDailyMaximum = hasAlignedValue(
            times = dto.daily?.time,
            values = dto.daily?.temperature2mMax
        )
        val hasDailyMinimum = hasAlignedValue(
            times = dto.daily?.time,
            values = dto.daily?.temperature2mMin
        )
        return !hasHourlyTemperature && !hasDailyMaximum && !hasDailyMinimum
    }

    /**
     * Une valeur n'est exploitable que si elle possède une échéance au même
     * index. Cette vérification évite de conserver un modèle dont l'API aurait
     * renvoyé un tableau de valeurs sans axe temporel correspondant : le
     * mapper l'éliminerait ensuite entièrement, mais le modèle serait malgré
     * tout compté à tort comme disponible.
     */
    private fun hasAlignedValue(
        times: List<String>?,
        values: List<Double?>?
    ): Boolean {
        if (times.isNullOrEmpty() || values.isNullOrEmpty()) return false
        val alignedSize = minOf(times.size, values.size)
        return (0 until alignedSize).any { index ->
            times[index].isNotBlank() &&
                ForecastPhysicalLimits.temperature(values[index]) != null
        }
    }

    // ────────────────── Reconstruction des DTOs hourly / daily ──────────────────

    private fun buildHourlyDto(
        json: JsonObject?,
        model: WeatherModel,
        singleModelMode: Boolean,
        sharedTime: List<String>
    ): HourlyDto? {
        if (json == null) return null
        val get = variableGetter(json, model, singleModelMode)
        return HourlyDto(
            time = sharedTime,
            temperature2m = get.doubles(HourlyVar.TEMPERATURE_2M),
            precipitation = get.doubles(HourlyVar.PRECIPITATION),
            windSpeed10m = get.doubles(HourlyVar.WIND_SPEED_10M),
            weatherCode = get.ints(HourlyVar.WEATHER_CODE),
            windDirection10m = get.ints(HourlyVar.WIND_DIRECTION_10M),
            precipitationProbability = get.ints(HourlyVar.PRECIPITATION_PROBABILITY),
            cloudCover = resolveCloudCover(get),
            windGusts10m = get.doubles(HourlyVar.WIND_GUSTS_10M)
        )
    }


    /**
     * Retourne la nébulosité totale fournie par Open-Meteo lorsqu'elle existe.
     *
     * AROME France HD expose un jeu natif réduit. Certaines réponses peuvent
     * omettre `cloud_cover` tout en fournissant les couches basse, moyenne et
     * haute. Dans ce cas on reconstruit le total avec la même combinaison que
     * le backend Open-Meteo : low×0,9 + mid×0,6 + high×0,3, bornée à 100 %.
     *
     * L'ancien fallback prenait le MAXIMUM des trois couches. Il surévaluait
     * fortement les voiles de nuages hauts (100 % high pouvait devenir 100 %
     * total) et pouvait donc fabriquer à tort un état OVERCAST. Si une des
     * trois couches manque à une échéance, on préfère désormais `null` plutôt
     * que d'inventer une couverture totale sous-estimée ou surestimée.
     */
    private fun resolveCloudCover(get: VariableGetter): List<Int?>? {
        val total = get.ints(HourlyVar.CLOUD_COVER)
        if (!total.isNullOrEmpty() && total.any { it != null }) return total

        val low = get.ints(HourlyVar.CLOUD_COVER_LOW)
        val mid = get.ints(HourlyVar.CLOUD_COVER_MID)
        val high = get.ints(HourlyVar.CLOUD_COVER_HIGH)
        val size = maxOf(low?.size ?: 0, mid?.size ?: 0, high?.size ?: 0)
        if (size == 0) return total

        return List(size) { index ->
            val lowValue = low?.getOrNull(index)?.takeIf { it in 0..100 }
            val midValue = mid?.getOrNull(index)?.takeIf { it in 0..100 }
            val highValue = high?.getOrNull(index)?.takeIf { it in 0..100 }
            if (lowValue == null || midValue == null || highValue == null) {
                null
            } else {
                (lowValue * 0.9 + midValue * 0.6 + highValue * 0.3)
                    .coerceAtMost(100.0)
                    .roundToInt()
            }
        }
    }

    private fun buildDailyDto(
        json: JsonObject?,
        model: WeatherModel,
        singleModelMode: Boolean,
        sharedTime: List<String>
    ): DailyDto? {
        if (json == null) return null
        val get = variableGetter(json, model, singleModelMode)
        return DailyDto(
            time = sharedTime,
            temperature2mMax = get.doubles(DailyVar.TEMPERATURE_2M_MAX),
            temperature2mMin = get.doubles(DailyVar.TEMPERATURE_2M_MIN),
            precipitationSum = get.doubles(DailyVar.PRECIPITATION_SUM),
            windSpeed10mMax = get.doubles(DailyVar.WIND_SPEED_10M_MAX),
            weatherCode = get.ints(DailyVar.WEATHER_CODE),
            windDirection10mDominant = get.ints(DailyVar.WIND_DIRECTION_10M_DOMINANT),
            precipitationProbabilityMax = get.ints(DailyVar.PRECIPITATION_PROBABILITY_MAX),
            windGusts10mMax = get.doubles(DailyVar.WIND_GUSTS_10M_MAX),
            // sunrise/sunset sont astronomiques et peuvent être renvoyés sans
            // suffixe même dans une réponse multi-modèles. On accepte donc
            // explicitement le champ partagé en fallback.
            sunrise = get.strings(DailyVar.SUNRISE, allowSharedFallback = true),
            sunset = get.strings(DailyVar.SUNSET, allowSharedFallback = true)
        )
    }

    // ────────────────── Getter typé + résolution suffixé/non-suffixé ────────────

    /**
     * Petit closure qui capture la logique de résolution "cherche
     * `<var>_<apiKey>` d'abord, sinon `<var>` si mode single-modèle".
     * Encapsulé pour éviter de dupliquer le if/else à chaque appel.
     */
    private class VariableGetter(
        private val json: JsonObject,
        private val apiKeys: List<String>,
        private val singleModelMode: Boolean
    ) {
        private fun lookup(baseKey: String, allowSharedFallback: Boolean = false): JsonElement? {
            apiKeys.forEach { apiKey ->
                json[baseKey + "_" + apiKey]?.let { return it }
            }
            // Fallback single-modèle : Open-Meteo omet le suffixe quand un
            // seul modèle est demandé (compat historique de leur API).
            if (singleModelMode || allowSharedFallback) json[baseKey]?.let { return it }
            return null
        }

        fun doubles(baseKey: String): List<Double?>? =
            lookup(baseKey)?.asNullableDoubles()

        fun ints(baseKey: String): List<Int?>? =
            lookup(baseKey)?.asNullableInts()

        fun strings(baseKey: String, allowSharedFallback: Boolean = false): List<String?>? =
            lookup(baseKey, allowSharedFallback)?.asNullableStrings()
    }

    private fun variableGetter(json: JsonObject, model: WeatherModel, singleModelMode: Boolean) =
        VariableGetter(
            json = json,
            apiKeys = listOf(model.apiKey) + model.apiKeyAliases,
            singleModelMode = singleModelMode
        )

    // ────────────────── Helpers de coercition JsonElement → List ────────────────

    private const val KEY_TIME = "time"

    /**
     * Convertit un JsonElement représentant `["2026-06-23T00:00", "2026-06-23T01:00", ...]`
     * en `List<String>`. Une position malformée devient une chaîne vide au
     * lieu d'être supprimée : le mapper la rejettera comme timestamp invalide
     * tout en retirant la valeur située au MÊME index. Filtrer ici décalerait
     * toutes les variables suivantes d'une heure ou d'un jour.
     */
    private fun JsonElement.asStringList(): List<String> =
        (this as? JsonArray)?.map { element ->
            (element as? JsonPrimitive)
                ?.takeIf(JsonPrimitive::isString)
                ?.content
                .orEmpty()
        }.orEmpty()

    /**
     * Convertit un JsonArray en `List<Double?>`. `JsonNull` devient `null`,
     * un primitif numérique devient sa valeur double, tout autre type devient
     * `null` silencieusement.
     */
    private fun JsonElement.asNullableDoubles(): List<Double?> =
        (this as? JsonArray)?.map { element ->
            when (element) {
                is JsonNull -> null
                is JsonPrimitive -> element.doubleOrNull
                else -> null
            }
        }.orEmpty()

    private fun JsonElement.asNullableInts(): List<Int?> =
        (this as? JsonArray)?.map { element ->
            when (element) {
                is JsonNull -> null
                is JsonPrimitive -> element.intOrNull ?: element.doubleOrNull?.let { value ->
                    if (value.isFinite() &&
                        value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() &&
                        abs(value - value.roundToInt()) < 1e-9
                    ) value.roundToInt() else null
                }
                else -> null
            }
        }.orEmpty()

    private fun JsonElement.asNullableStrings(): List<String?> =
        (this as? JsonArray)?.map { element ->
            when (element) {
                is JsonNull -> null
                is JsonPrimitive -> element.takeIf(JsonPrimitive::isString)?.content
                else -> null
            }
        }.orEmpty()

    // ────────────────── Noms des variables (constantes de scope) ────────────────

    private object HourlyVar {
        const val TEMPERATURE_2M = "temperature_2m"
        const val PRECIPITATION = "precipitation"
        const val WIND_SPEED_10M = "wind_speed_10m"
        const val WEATHER_CODE = "weather_code"
        const val WIND_DIRECTION_10M = "wind_direction_10m"
        const val WIND_GUSTS_10M = "wind_gusts_10m"
        const val PRECIPITATION_PROBABILITY = "precipitation_probability"
        const val CLOUD_COVER = "cloud_cover"
        const val CLOUD_COVER_LOW = "cloud_cover_low"
        const val CLOUD_COVER_MID = "cloud_cover_mid"
        const val CLOUD_COVER_HIGH = "cloud_cover_high"
    }

    private object DailyVar {
        const val TEMPERATURE_2M_MAX = "temperature_2m_max"
        const val TEMPERATURE_2M_MIN = "temperature_2m_min"
        const val PRECIPITATION_SUM = "precipitation_sum"
        const val WIND_SPEED_10M_MAX = "wind_speed_10m_max"
        const val WIND_GUSTS_10M_MAX = "wind_gusts_10m_max"
        const val WEATHER_CODE = "weather_code"
        const val WIND_DIRECTION_10M_DOMINANT = "wind_direction_10m_dominant"
        const val PRECIPITATION_PROBABILITY_MAX = "precipitation_probability_max"
        const val SUNRISE = "sunrise"
        const val SUNSET = "sunset"
    }
}
