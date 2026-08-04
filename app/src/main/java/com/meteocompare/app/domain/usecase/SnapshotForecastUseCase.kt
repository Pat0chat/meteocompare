package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ForecastBiasRecord
import java.time.Clock
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
 * [FetchBiasObservationsUseCase] ne téléchargera la référence historique
 * que pour les jours réellement couverts par un snapshot. Sans prévision
 * correspondante, aucun couple ne peut entrer dans le calcul : la fiabilité
 * dispose simplement de moins de jours, sans fabriquer de donnée.
 *
 * ## Idempotence
 *
 * `recordForecast` utilise `OnConflictStrategy.REPLACE` sur la PK composite
 * `(cityId, modelKey, variable, targetDate, issuedAt)`. Ici [issuedAt] est
 * normalisé au début de la journée locale : tous les refreshs d'une même
 * journée remplacent donc le même snapshot J+1. Cette clé stable borne le
 * stockage sans perdre l'échéance de comparaison retenue.
 *
 * ## Horizon vérifié
 *
 * Le suivi de fiabilité compare uniquement la prévision du lendemain (J+1).
 * Mélanger une prévision faite le jour même avec une prévision à J+5 rendrait
 * le score impossible à interpréter. Pour chaque refresh, seule la date
 * [today] + 1 est donc enregistrée.
 *
 * L'instant de clé est normalisé au début de la journée locale d'émission.
 * Les refreshs successifs de la même journée remplacent ainsi la même ligne
 * Room : trois valeurs par modèle et par jour au maximum, sans croissance liée
 * au nombre d'ouvertures de l'application.
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
    private val biasRepository: BiasSampleRepository,
    private val clock: Clock = Clock.systemUTC()
) {

    /**
     * @param forecast le résultat frais de `refreshCityForecast`.
     * @param issuedAt instant du refresh. Il sert à déterminer la journée
     *   locale d'émission ; la clé persistée est ensuite normalisée au début
     *   de cette journée. Par défaut, utilise l'horloge injectée.
     * @param today date civile de référence dans le fuseau de la ville. Elle
     *   est dérivée de [issuedAt] pour éviter un décalage autour de minuit.
     */
    suspend operator fun invoke(
        forecast: CityForecast,
        issuedAt: Instant = clock.instant(),
        today: LocalDate = issuedAt.localDateIn(forecast.city.timezone)
    ) {
        val cityId = forecast.city.id
        val targetDay = today.plusDays(1)
        val issueDayMarker = today
            .atStartOfDay(resolveZoneOrUtc(forecast.city.timezone))
            .toInstant()

        val records = ArrayList<ForecastBiasRecord>(forecast.seriesByModel.size * 3)
        for ((model, series) in forecast.seriesByModel) {
            val daily = series.daily
            val index = daily.dates.indexOf(targetDay)
            if (index < 0) continue

            // Chaque variable est indépendante. Un cache ancien, un modèle
            // partiel ou une série tronquée ne doit pas faire perdre les
            // autres valeurs valides du même jour.
            daily.tempMax.getOrNull(index)?.let { value ->
                records += ForecastBiasRecord(
                    cityId, model, BiasVariable.TEMPERATURE, targetDay, issueDayMarker, value
                )
            }
            daily.precipitationSum.getOrNull(index)?.let { value ->
                records += ForecastBiasRecord(
                    cityId, model, BiasVariable.PRECIPITATION, targetDay, issueDayMarker, value
                )
            }
            daily.windSpeedMax.getOrNull(index)?.let { value ->
                records += ForecastBiasRecord(
                    cityId, model, BiasVariable.WIND_SPEED, targetDay, issueDayMarker, value
                )
            }
        }
        biasRepository.recordForecasts(records)
    }
}
