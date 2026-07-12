package com.meteocompare.app.data.worker

import com.meteocompare.app.data.local.ForecastCacheDao
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.BackfillHistoricalForecastUseCase
import com.meteocompare.app.domain.usecase.FetchBiasObservationsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bridge Hilt → [BiasRefreshWorker]. Un CoroutineWorker n'est PAS une
 * @AndroidEntryPoint et le projet a fait le choix de NE PAS ajouter
 * `androidx.hilt:hilt-work` — même pattern que pour le widget. On récupère
 * les dépendances via
 *
 *   EntryPointAccessors.fromApplication(context, BiasRefreshEntryPoint::class.java)
 *
 * Installé dans [SingletonComponent] parce que ce qu'on expose (repositories
 * + use case) est déjà `@Singleton` et vit à l'échelle de l'app.
 *
 * Voir le KDoc de [com.meteocompare.app.widget.WidgetEntryPoint] pour le
 * rationnel technique complet du pattern.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BiasRefreshEntryPoint {
    fun cityRepository(): CityRepository
    fun biasSampleRepository(): BiasSampleRepository
    fun userPreferencesRepository(): UserPreferencesRepository
    fun fetchBiasObservationsUseCase(): FetchBiasObservationsUseCase
    fun backfillHistoricalForecastUseCase(): BackfillHistoricalForecastUseCase

    /**
     * Exposé pour le housekeeping quotidien du cache forecast — voir
     * l'étage 3 du docblock de [BiasRefreshWorker.doWork]. Le DAO est
     * `@Singleton` (partagé avec `ForecastRepositoryImpl`), aucune
     * contention ne peut apparaître entre le fetch d'un utilisateur qui
     * insère et le worker qui purge (Room sérialise les writes).
     */
    fun forecastCacheDao(): ForecastCacheDao
}
