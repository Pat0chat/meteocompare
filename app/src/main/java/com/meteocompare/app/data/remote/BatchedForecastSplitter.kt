package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import com.meteocompare.app.data.remote.dto.DailyDto
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.data.remote.dto.HourlyDto
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

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
 * le DTO reconstruit, ce que le mapper interprète comme "modèle sans cette
 * variable" (comportement identique à AROME HD qui manque
 * `precipitation_probability` par exemple).
 *
 * ─── Détection d'échec par modèle ────────────────────────────────────────
 * Un modèle qui n'a répondu à AUCUNE variable temporelle (typiquement hors
 * de sa zone de couverture) donnera un [ForecastResponseDto] avec un
 * `hourly.temperature_2m` entièrement null OU absent. C'est l'appelant qui
 * décide comment traiter ce cas — voir [hasNoUsableData] pour un helper.
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
     * Vrai si la série retournée pour ce modèle est totalement inexploitable :
     * pas de température horaire du tout. Sert à filtrer les modèles pour
     * lesquels Open-Meteo a envoyé la structure mais aucune valeur (typiquement
     * modèle régional hors de sa zone de couverture, cf. AROME hors France).
     *
     * Choix de la température comme indicateur : c'est la variable présente
     * dans tous les modèles utilisés par l'app. Si elle est vide/entièrement
     * null, aucune vue de l'app ne peut afficher quoi que ce soit d'utile.
     */
    private fun hasNoUsableData(dto: ForecastResponseDto): Boolean {
        val temps = dto.hourly?.temperature2m
        return temps.isNullOrEmpty() || temps.all { it == null }
    }

    // ────────────────── Reconstruction des DTOs hourly / daily ──────────────────

    private fun buildHourlyDto(
        json: JsonObject?,
        model: WeatherModel,
        singleModelMode: Boolean,
        sharedTime: List<String>
    ): HourlyDto? {
        if (json == null) return null
        val get = variableGetter(json, model.apiKey, singleModelMode)
        return HourlyDto(
            time = sharedTime,
            temperature2m = get.doubles(HourlyVar.TEMPERATURE_2M),
            precipitation = get.doubles(HourlyVar.PRECIPITATION),
            windSpeed10m = get.doubles(HourlyVar.WIND_SPEED_10M),
            weatherCode = get.ints(HourlyVar.WEATHER_CODE),
            windDirection10m = get.ints(HourlyVar.WIND_DIRECTION_10M),
            precipitationProbability = get.ints(HourlyVar.PRECIPITATION_PROBABILITY),
            cloudCover = get.ints(HourlyVar.CLOUD_COVER)
        )
    }

    private fun buildDailyDto(
        json: JsonObject?,
        model: WeatherModel,
        singleModelMode: Boolean,
        sharedTime: List<String>
    ): DailyDto? {
        if (json == null) return null
        val get = variableGetter(json, model.apiKey, singleModelMode)
        return DailyDto(
            time = sharedTime,
            temperature2mMax = get.doubles(DailyVar.TEMPERATURE_2M_MAX),
            temperature2mMin = get.doubles(DailyVar.TEMPERATURE_2M_MIN),
            precipitationSum = get.doubles(DailyVar.PRECIPITATION_SUM),
            windSpeed10mMax = get.doubles(DailyVar.WIND_SPEED_10M_MAX),
            weatherCode = get.ints(DailyVar.WEATHER_CODE),
            windDirection10mDominant = get.ints(DailyVar.WIND_DIRECTION_10M_DOMINANT),
            precipitationProbabilityMax = get.ints(DailyVar.PRECIPITATION_PROBABILITY_MAX)
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
        private val apiKey: String,
        private val singleModelMode: Boolean
    ) {
        private fun lookup(baseKey: String): JsonElement? {
            json[baseKey + "_" + apiKey]?.let { return it }
            // Fallback single-modèle : Open-Meteo omet le suffixe quand un
            // seul modèle est demandé (compat historique de leur API).
            if (singleModelMode) json[baseKey]?.let { return it }
            return null
        }

        fun doubles(baseKey: String): List<Double?>? =
            lookup(baseKey)?.asNullableDoubles()

        fun ints(baseKey: String): List<Int?>? =
            lookup(baseKey)?.asNullableInts()
    }

    private fun variableGetter(json: JsonObject, apiKey: String, singleModelMode: Boolean) =
        VariableGetter(json, apiKey, singleModelMode)

    // ────────────────── Helpers de coercition JsonElement → List ────────────────

    private const val KEY_TIME = "time"

    /**
     * Convertit un JsonElement représentant `["2026-06-23T00:00", "2026-06-23T01:00", ...]`
     * en `List<String>`. Silencieusement filtre les éléments qui ne seraient pas
     * des String — défense en profondeur contre une réponse malformée.
     */
    private fun JsonElement.asStringList(): List<String> = try {
        (this as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
        } ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    /**
     * Convertit un JsonArray en `List<Double?>`. `JsonNull` devient `null`,
     * un primitif numérique devient sa valeur double, tout autre type devient
     * `null` silencieusement.
     */
    private fun JsonElement.asNullableDoubles(): List<Double?> = try {
        jsonArray.map { element ->
            when (element) {
                is JsonNull -> null
                is JsonPrimitive -> element.doubleOrNull
                else -> null
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun JsonElement.asNullableInts(): List<Int?> = try {
        jsonArray.map { element ->
            when (element) {
                is JsonNull -> null
                is JsonPrimitive -> element.intOrNull
                else -> null
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }

    // ────────────────── Noms des variables (constantes de scope) ────────────────

    private object HourlyVar {
        const val TEMPERATURE_2M = "temperature_2m"
        const val PRECIPITATION = "precipitation"
        const val WIND_SPEED_10M = "wind_speed_10m"
        const val WEATHER_CODE = "weather_code"
        const val WIND_DIRECTION_10M = "wind_direction_10m"
        const val PRECIPITATION_PROBABILITY = "precipitation_probability"
        const val CLOUD_COVER = "cloud_cover"
    }

    private object DailyVar {
        const val TEMPERATURE_2M_MAX = "temperature_2m_max"
        const val TEMPERATURE_2M_MIN = "temperature_2m_min"
        const val PRECIPITATION_SUM = "precipitation_sum"
        const val WIND_SPEED_10M_MAX = "wind_speed_10m_max"
        const val WEATHER_CODE = "weather_code"
        const val WIND_DIRECTION_10M_DOMINANT = "wind_direction_10m_dominant"
        const val PRECIPITATION_PROBABILITY_MAX = "precipitation_probability_max"
    }
}
