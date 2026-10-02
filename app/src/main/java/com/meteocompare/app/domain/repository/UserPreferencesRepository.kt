package com.meteocompare.app.domain.repository

import com.meteocompare.app.domain.model.UnitSystem

import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.CityDetailSection
import com.meteocompare.app.domain.model.CityDetailViewMode
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.NotificationSettings
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.coroutines.flow.Flow

/**
 * Préférences utilisateur persistantes — modèles sélectionnés, thème, langue,
 * intervalle de rafraîchissement et organisation des écrans.
 */
interface UserPreferencesRepository {

    fun observeEnabledModels(): Flow<List<WeatherModel>>
    suspend fun setEnabledModels(models: List<WeatherModel>)

    /** Presentation only; independent of locale and forecast settings. */
    fun observeUnitSystem(): Flow<UnitSystem>
    suspend fun setUnitSystem(system: UnitSystem)

    fun observeThemePreference(): Flow<ThemePreference>
    suspend fun setThemePreference(preference: ThemePreference)

    fun observeLanguagePreference(): Flow<LanguagePreference>
    suspend fun setLanguagePreference(preference: LanguagePreference)

    /**
     * Intervalle entre deux rafraîchissements automatiques des données.
     * Utilisé par le widget et l'app comme seuil de fraîcheur réseau du cache.
     * La cadence de présentation horaire est volontairement indépendante.
     */
    fun observeRefreshInterval(): Flow<RefreshInterval>
    suspend fun setRefreshInterval(interval: RefreshInterval)

    /** Moteur de prévision central utilisé par Home, Détails et widgets. */
    fun observeForecastEngine(): Flow<ForecastEngine>
    suspend fun setForecastEngine(engine: ForecastEngine)

    /**
     * Sections repliées de la fiche d'une ville. La préférence est mémorisée
     * séparément pour chaque ville afin de conserver une organisation adaptée
     * à chaque lieu après fermeture ou redémarrage de l'application.
     */
    fun observeCollapsedCityDetailSections(cityId: String): Flow<Set<CityDetailSection>>

    suspend fun setCityDetailSectionCollapsed(
        cityId: String,
        section: CityDetailSection,
        collapsed: Boolean
    )

    /** Dernière granularité consultée dans la comparaison détaillée de la ville. */
    fun observeCityDetailViewMode(cityId: String): Flow<CityDetailViewMode>
    suspend fun setCityDetailViewMode(cityId: String, mode: CityDetailViewMode)

    /** Dernière famille de données consultée dans la comparaison détaillée. */
    fun observeCityDetailContentTab(cityId: String): Flow<CityDetailContentTab>
    suspend fun setCityDetailContentTab(cityId: String, tab: CityDetailContentTab)

    /** Notifications météo locales (résumé quotidien, divergence, changement). */
    fun observeNotificationSettings(): Flow<NotificationSettings>

    /**
     * Applique [transform] de façon atomique à la valeur persistée et retourne
     * la nouvelle valeur. Deux modifications rapprochées ne peuvent donc pas se
     * réécrire mutuellement à partir d'un état UI encore ancien.
     */
    suspend fun updateNotificationSettings(
        transform: (NotificationSettings) -> NotificationSettings
    ): NotificationSettings
}
