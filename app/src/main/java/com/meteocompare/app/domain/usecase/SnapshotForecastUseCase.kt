package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ForecastBiasRecord
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshotte les prévisions daily de tous les modèles d'un [CityForecast]
 * fraîchement fetché dans le repo de biais, pour alimenter l'historique
 * (`forecast_samples`).
 *
 * ## Pourquoi piggybacker sur le refresh utilisateur
 *
 * À chaque fois qu'un utilisateur ouvre CityDetail ou rafraîchit sa liste,
 * l'app fetche déjà les prévisions fraîches. Un snapshot dans la foulée est
 * **gratuit HTTP-side** : on utilise la donnée qui vient d'atterrir en
 * mémoire. C'est l'inverse d'un worker qui devrait re-fetcher pour
 * snapshotter — pas de double appel, pas de quota gaspillé.
 *
 * Contrepartie : si l'utilisateur n'ouvre pas l'app pendant 3 jours, on
 * manque 3 jours de snapshots pour ce modèle. Le worker quotidien de
 * [FetchBiasObservationsUseCase] fetchera quand même les observations
 * rétrospectivement, mais sans forecast correspondant → le JOIN INNER dans
 * la DAO filtrera ces jours, et le calcul de biais tournera avec moins de
 * samples. Acceptable dégradation gracieuse.
 *
 * ## Idempotence
 *
 * `recordForecast` utilise `OnConflictStrategy.REPLACE` sur la PK composite
 * `(cityId, modelKey, variable, targetDate, issuedAt)`. Deux refresh
 * successifs au sein de la même milliseconde (issuedAt identique) écrasent
 * — cas théorique impossible en pratique (la latence HTTP est ≫ ms). Deux
 * refresh à des ms différentes créent deux snapshots côte à côte, sans
 * conflit et sans perte.
 *
 * ## Sur quels jours snapshotter
 *
 * Tous les jours présents dans [CityForecast.daily.dates] — l'API renvoie
 * typiquement 7 jours. Les jours futurs seront rejoignables avec des
 * observations quand ils passeront dans le passé (via
 * [FetchBiasObservationsUseCase]).
 *
 * Filtrage : on snapshotte les jours dans la fenêtre `[today - 35, today + 10]`
 * pour se prémunir contre un modèle qui renverrait des dates aberrantes (bug
 * de parsing, ancien cache pré-Instant). La marge haute large (+10) accepte
 * les modèles avec un long horizon.
 *
 * ## Robustesse
 *
 * Les valeurs `null` dans les listes (jour sans prévision pour cette
 * variable) sont skippées silencieusement. Un modèle qui ne fournirait qu'une
 * variable sur 3 (rare, mais possible pour des modèles régionaux avec des
 * variables non couvertes) crée simplement moins de rows en base.
 */
@Singleton
class SnapshotForecastUseCase @Inject constructor(
    private val biasRepository: BiasSampleRepository
) {

    /**
     * @param forecast le résultat frais de `refreshCityForecast`.
     * @param issuedAt l'instant du snapshot. Défaut = [Instant.now]. Passer
     *   explicitement en test pour la reproductibilité.
     * @param today la date "aujourd'hui" pour le filtrage de la fenêtre.
     *   Défaut = [LocalDate.now]. Test-friendly comme [issuedAt].
     */
    suspend operator fun invoke(
        forecast: CityForecast,
        issuedAt: Instant = Instant.now(),
        today: LocalDate = LocalDate.now()
    ) {
        val cityId = forecast.city.id
        // Fenêtre de sanité : rejette les dates aberrantes qui pourraient venir
        // d'un cache corrompu ou d'un futur bug de parsing.
        val minDay = today.minusDays(35).toEpochDay()
        val maxDay = today.plusDays(10).toEpochDay()

        val records = ArrayList<ForecastBiasRecord>(forecast.seriesByModel.size * 21)
        for ((model, series) in forecast.seriesByModel) {
            val daily = series.daily
            val dates = daily.dates

            for (i in dates.indices) {
                val date = dates[i]
                val epochDay = date.toEpochDay()
                if (epochDay !in minDay..maxDay) continue

                // Chaque variable est indépendante. Un cache ancien, un modèle
                // partiel ou une série tronquée ne doit pas faire perdre les
                // autres valeurs valides du même jour.
                daily.tempMax.getOrNull(i)?.let { value ->
                    records += ForecastBiasRecord(
                        cityId, model, BiasVariable.TEMPERATURE, date, issuedAt, value
                    )
                }
                daily.precipitationSum.getOrNull(i)?.let { value ->
                    records += ForecastBiasRecord(
                        cityId, model, BiasVariable.PRECIPITATION, date, issuedAt, value
                    )
                }
                daily.windSpeedMax.getOrNull(i)?.let { value ->
                    records += ForecastBiasRecord(
                        cityId, model, BiasVariable.WIND_SPEED, date, issuedAt, value
                    )
                }
            }
        }
        biasRepository.recordForecasts(records)
    }
}
