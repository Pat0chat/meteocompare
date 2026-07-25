package com.meteocompare.app.domain.repository

import com.meteocompare.app.domain.model.CityDetailSection
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.CityDetailViewMode
import com.meteocompare.app.domain.model.LanguagePreference
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

    fun observeThemePreference(): Flow<ThemePreference>
    suspend fun setThemePreference(preference: ThemePreference)

    fun observeLanguagePreference(): Flow<LanguagePreference>
    suspend fun setLanguagePreference(preference: LanguagePreference)

    /**
     * Intervalle entre deux rafraîchissements automatiques des données.
     * Utilisé par le widget (cadence WorkManager) et l'app (seuil de fraîcheur
     * du cache avant refetch au chargement d'écran).
     */
    fun observeRefreshInterval(): Flow<RefreshInterval>
    suspend fun setRefreshInterval(interval: RefreshInterval)

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
}

