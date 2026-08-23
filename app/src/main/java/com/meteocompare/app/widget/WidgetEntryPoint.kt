package com.meteocompare.app.widget

import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bridge Hilt → widget. Un widget n'est PAS une @AndroidEntryPoint (les
 * receivers/providers de widget ne sont pas des Activities/Services), donc on
 * ne peut pas y injecter directement. Le pattern officiel Hilt pour ce cas est
 * l'EntryPoint : on récupère la implémentation via
 *
 *   EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
 *
 * Installé dans [SingletonComponent] parce que les dépendances exposées
 * (repositories + calculator) sont elles-mêmes @Singleton et vivent à
 * l'échelle de l'app, pas d'une activité.
 *
 * On expose aussi [UserPreferencesRepository] pour que [MeteoWidget] et
 * [WidgetRefreshScheduler] puissent lire l'intervalle de rafraîchissement
 * choisi par l'utilisateur (utilisé comme seuil `maxCacheAgeMs` dans le
 * repository, et comme cadence WorkManager).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun cityRepository(): CityRepository
    fun forecastRepository(): ForecastRepository
    fun confidenceCalculator(): ConfidenceCalculator
    fun forecastEngineContextProvider(): ForecastEngineContextProvider
    fun userPreferencesRepository(): UserPreferencesRepository
}
