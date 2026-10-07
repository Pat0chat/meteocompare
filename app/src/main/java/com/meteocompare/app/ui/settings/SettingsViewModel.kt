package com.meteocompare.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.await
import com.meteocompare.app.R
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.data.worker.BiasRefreshScheduler
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.NotificationSettings
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.notification.WeatherNotificationScheduler
import com.meteocompare.app.ui.components.AppToastEvent
import com.meteocompare.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ModelSelectionCommitResult {
    UNCHANGED,
    SAVED,
    SAVED_WIDGET_REFRESH_DELAYED,
    FAILED
}

private enum class PreferenceUpdateResult {
    UNCHANGED,
    SAVED,
    FAILED
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val prefs: UserPreferencesRepository,
    private val cityRepository: CityRepository
) : ViewModel() {

    private val modelUpdateMutex = Mutex()
    private val _feedback = Channel<AppToastEvent>(capacity = Channel.BUFFERED)
    val feedback = _feedback.receiveAsFlow()

    /**
     * Sélection persistée, distincte du brouillon de l'écran Settings.
     *
     * Les ViewModels météo observent directement [UserPreferencesRepository.observeEnabledModels].
     * Écrire dans DataStore à chaque case cochée/décochée leur ferait donc annuler/recréer leurs
     * streams réseau pour chaque tap. On garde ici un brouillon local et on ne publie la sélection
     * finale qu'une seule fois quand l'utilisateur quitte les réglages.
     */
    private val persistedEnabledModels: StateFlow<Set<WeatherModel>> = prefs.observeEnabledModels()
        .map { it.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = WeatherModel.MVP_SELECTION.toSet()
        )

    private val pendingEnabledModels = MutableStateFlow<Set<WeatherModel>?>(null)

    val enabledModels: StateFlow<Set<WeatherModel>> = combine(
        persistedEnabledModels,
        pendingEnabledModels
    ) { persisted, pending -> pending ?: persisted }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherModel.MVP_SELECTION.toSet()
        )

    val unitSystem: StateFlow<UnitSystem> = prefs.observeUnitSystem()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitSystem.METRIC)

    val themePreference: StateFlow<ThemePreference> = prefs.observeThemePreference()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemePreference.SYSTEM
        )

    val languagePreference: StateFlow<LanguagePreference> = prefs.observeLanguagePreference()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LanguagePreference.SYSTEM
        )

    val refreshInterval: StateFlow<RefreshInterval> = prefs.observeRefreshInterval()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RefreshInterval.DEFAULT
        )

    val forecastEngine: StateFlow<ForecastEngine> = prefs.observeForecastEngine()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ForecastEngine.DEFAULT
        )

    val notificationSettings: StateFlow<NotificationSettings> = prefs.observeNotificationSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationSettings()
        )

    /** Villes proposées pour les notifications : uniquement les favoris. */
    val favoriteCities: StateFlow<List<City>> = cityRepository.observeFavorites()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun onModelToggled(model: WeatherModel, enabled: Boolean) {
        viewModelScope.launch {
            val warning = modelUpdateMutex.withLock {
                // Au tout premier tap, relire la source de vérité plutôt que de
                // supposer que le StateFlow eager a déjà reçu sa première valeur.
                // Les taps suivants partent du brouillon local. Le mutex conserve
                // aussi l'ordre si l'utilisateur coche plusieurs cases très vite.
                val current = pendingEnabledModels.value
                    ?: prefs.observeEnabledModels().first().toSet()
                val next = if (enabled) current + model else current - model
                if (next.isEmpty()) {
                    true
                } else {
                    // Important : aucune écriture DataStore ici. Tant que
                    // l'utilisateur édite plusieurs cases, aucun collecteur météo
                    // ne voit de configuration intermédiaire et donc aucun refresh
                    // réseau n'est relancé pour chaque tap.
                    pendingEnabledModels.value = next
                    false
                }
            }
            if (warning) {
                _feedback.send(AppToastEvent.warning(R.string.settings_models_min_warning))
            }
        }
    }

    /**
     * Publie en une seule écriture la sélection finale des modèles.
     *
     * Le résultat distingue une vraie sauvegarde d'un simple Back sans changement :
     * l'écran peut ainsi confirmer uniquement les modifications effectivement persistées.
     */
    suspend fun commitModelSelectionResult(): ModelSelectionCommitResult = modelUpdateMutex.withLock {
        val pending = pendingEnabledModels.value
            ?: return@withLock ModelSelectionCommitResult.UNCHANGED
        // Relire la source persistée réelle ici : le StateFlow UI possède une
        // valeur initiale optimiste et ne doit jamais décider qu'un commit est
        // inutile avant sa première émission DataStore.
        val persisted = prefs.observeEnabledModels().first().toSet()
        if (pending == persisted) {
            pendingEnabledModels.value = null
            return@withLock ModelSelectionCommitResult.UNCHANGED
        }

        val result = runSuspendCatching {
            prefs.setEnabledModels(pending.toList())
            // DataStore.edit() est terminé quand setEnabledModels retourne, mais
            // attendre la réémission évite un bref retour visuel à l'ancien set
            // lorsque le brouillon est supprimé.
            prefs.observeEnabledModels().first { it.toSet() == pending }
        }
        if (result.isFailure) {
            _feedback.send(AppToastEvent.error(R.string.toast_settings_save_error))
            return@withLock ModelSelectionCommitResult.FAILED
        }

        pendingEnabledModels.value = null
        if (triggerWidgetRefreshSafely()) {
            ModelSelectionCommitResult.SAVED
        } else {
            ModelSelectionCommitResult.SAVED_WIDGET_REFRESH_DELAYED
        }
    }

    /** Compatibilité avec les appels/tests historiques qui n'ont besoin que du succès. */
    suspend fun commitModelSelection(): Boolean =
        commitModelSelectionResult() != ModelSelectionCommitResult.FAILED


    /**
     * Demande un cycle exceptionnel de collecte des biais. Le scheduler
     * conserve les contraintes réseau/batterie, le mutex global et la
     * déduplication WorkManager ; ce bouton ne modifie pas la cadence
     * quotidienne normale.
     */
    fun onBiasRefreshRequested() {
        viewModelScope.launch {
            val feedback = runSuspendCatching {
                BiasRefreshScheduler.triggerManualRefresh(appContext).await()
            }.fold(
                onSuccess = { AppToastEvent.info(R.string.settings_bias_refresh_queued) },
                onFailure = { AppToastEvent.error(R.string.toast_action_error) }
            )
            _feedback.send(feedback)
        }
    }

    fun onUnitSystemSelected(system: UnitSystem) {
        viewModelScope.launch {
            when (updatePreferenceIfChanged(
                current = { prefs.observeUnitSystem().first() },
                target = system,
                persist = { prefs.setUnitSystem(system) }
            )) {
                PreferenceUpdateResult.UNCHANGED -> Unit
                PreferenceUpdateResult.FAILED ->
                    _feedback.send(AppToastEvent.error(R.string.toast_settings_save_error))
                PreferenceUpdateResult.SAVED -> {
                    _feedback.send(
                        if (triggerWidgetRefreshSafely()) {
                            AppToastEvent.success(R.string.toast_units_updated)
                        } else {
                            AppToastEvent.warning(R.string.toast_widget_refresh_delayed)
                        }
                    )
                }
            }
        }
    }

    fun onThemeSelected(preference: ThemePreference) {
        viewModelScope.launch {
            when (updatePreferenceIfChanged(
                current = { prefs.observeThemePreference().first() },
                target = preference,
                persist = { prefs.setThemePreference(preference) }
            )) {
                PreferenceUpdateResult.UNCHANGED -> Unit
                PreferenceUpdateResult.FAILED ->
                    _feedback.send(AppToastEvent.error(R.string.toast_settings_save_error))
                PreferenceUpdateResult.SAVED ->
                    _feedback.send(AppToastEvent.success(R.string.toast_theme_updated))
            }
        }
    }

    /**
     * Persiste la langue dans l'unique stockage canonique. Cette fonction est
     * suspendue afin que l'écran puisse attendre la fin de l'écriture avant
     * `Activity.recreate()` et éviter toute course avec attachBaseContext().
     *
     * @return `true` uniquement lorsqu'une nouvelle valeur a réellement été
     * persistée. L'écran évite ainsi un `Activity.recreate()` inutile quand
     * l'utilisateur retouche la langue déjà active.
     */
    suspend fun onLanguageSelected(preference: LanguagePreference): Boolean =
        when (updatePreferenceIfChanged(
            current = { prefs.observeLanguagePreference().first() },
            target = preference,
            persist = { prefs.setLanguagePreference(preference) }
        )) {
            PreferenceUpdateResult.UNCHANGED -> false
            PreferenceUpdateResult.SAVED -> true
            PreferenceUpdateResult.FAILED -> {
                _feedback.send(AppToastEvent.error(R.string.toast_settings_save_error))
                false
            }
        }

    /**
     * Persiste le nouvel intervalle de rafraîchissement et propage
     * immédiatement le changement au widget.
     *
     * La cadence de présentation du widget reste fixe à 15 minutes ; ce
     * réglage pilote uniquement `maxCacheAgeMs`. Le tick est déclenché après
     * la persistance afin qu'il relise immédiatement la nouvelle politique.
     */
    fun onRefreshIntervalSelected(interval: RefreshInterval) {
        viewModelScope.launch {
            when (updatePreferenceIfChanged(
                current = { prefs.observeRefreshInterval().first() },
                target = interval,
                persist = { prefs.setRefreshInterval(interval) }
            )) {
                PreferenceUpdateResult.UNCHANGED -> Unit
                PreferenceUpdateResult.FAILED ->
                    _feedback.send(AppToastEvent.error(R.string.toast_settings_save_error))
                PreferenceUpdateResult.SAVED -> {
                    _feedback.send(
                        if (triggerWidgetRefreshSafely()) {
                            AppToastEvent.success(R.string.toast_refresh_interval_updated)
                        } else {
                            AppToastEvent.warning(R.string.toast_widget_refresh_delayed)
                        }
                    )
                }
            }
        }
    }

    /**
     * Change uniquement la stratégie de centrale : aucune requête réseau n'est
     * nécessaire. Le widget est toutefois rafraîchi immédiatement pour relire
     * la préférence et recalculer ses valeurs depuis le cache partagé.
     */
    fun onForecastEngineSelected(engine: ForecastEngine) {
        viewModelScope.launch {
            when (updatePreferenceIfChanged(
                current = { prefs.observeForecastEngine().first() },
                target = engine,
                persist = { prefs.setForecastEngine(engine) }
            )) {
                PreferenceUpdateResult.UNCHANGED -> Unit
                PreferenceUpdateResult.FAILED ->
                    _feedback.send(AppToastEvent.error(R.string.toast_settings_save_error))
                PreferenceUpdateResult.SAVED -> {
                    _feedback.send(
                        if (triggerWidgetRefreshSafely()) {
                            AppToastEvent.success(R.string.toast_forecast_engine_updated)
                        } else {
                            AppToastEvent.warning(R.string.toast_widget_refresh_delayed)
                        }
                    )
                }
            }
        }
    }

    fun onDailySummaryToggled(enabled: Boolean) = updateNotificationSettings(
        transform = { it.copy(dailySummaryEnabled = enabled) },
        successFeedback = { _, _, _ ->
            AppToastEvent.success(
                if (enabled) R.string.toast_notifications_daily_enabled
                else R.string.toast_notifications_daily_disabled
            )
        }
    )

    fun onDailySummaryTimeSelected(time: LocalTime) = updateNotificationSettings(
        transform = { it.copy(dailySummaryTime = time) },
        successFeedback = { _, _, _ ->
            AppToastEvent.success(R.string.toast_notifications_time_updated)
        }
    )

    fun onDivergenceAlertsToggled(enabled: Boolean) = updateNotificationSettings(
        transform = { it.copy(divergenceAlertsEnabled = enabled) },
        successFeedback = { _, _, _ ->
            AppToastEvent.success(
                if (enabled) R.string.toast_notifications_divergence_enabled
                else R.string.toast_notifications_divergence_disabled
            )
        }
    )

    fun onForecastChangeAlertsToggled(enabled: Boolean) = updateNotificationSettings(
        transform = { it.copy(forecastChangeAlertsEnabled = enabled) },
        successFeedback = { _, _, _ ->
            AppToastEvent.success(
                if (enabled) R.string.toast_notifications_change_enabled
                else R.string.toast_notifications_change_disabled
            )
        }
    )

    fun onNotificationCityToggled(cityId: String, followed: Boolean) = updateNotificationSettings(
        transform = { settings ->
            settings.copy(
                cityIds = if (followed) settings.cityIds + cityId else settings.cityIds - cityId
            )
        },
        successFeedback = { _, _, favorites ->
            favorites.firstOrNull { it.id == cityId }?.let { city ->
                AppToastEvent.success(
                    if (followed) R.string.toast_notifications_city_enabled
                    else R.string.toast_notifications_city_disabled,
                    city.name
                )
            } ?: AppToastEvent.success(R.string.toast_notifications_cities_updated)
        }
    )

    /**
     * Persiste atomiquement la modification puis replanifie les travaux.
     *
     * À la toute première activation, la première ville favorite est suivie
     * par défaut : sans ville, une notification activée ne produirait rien et
     * l'utilisateur pourrait croire la fonction cassée. Une ville décochée
     * ensuite reste décochée.
     */
    private fun updateNotificationSettings(
        transform: (NotificationSettings) -> NotificationSettings,
        successFeedback: (
            previous: NotificationSettings,
            updated: NotificationSettings,
            favorites: List<City>
        ) -> AppToastEvent
    ) {
        viewModelScope.launch {
            var previous = NotificationSettings()
            var favorites = emptyList<City>()
            val updated = runSuspendCatching {
                favorites = cityRepository.observeFavorites().first()
                prefs.updateNotificationSettings { current ->
                    previous = current
                    val next = transform(current)
                    val firstActivation = !current.anyEnabled && next.anyEnabled
                    if (firstActivation && next.cityIds.isEmpty() && favorites.isNotEmpty()) {
                        next.copy(cityIds = setOf(favorites.first().id))
                    } else {
                        next
                    }
                }
            }.getOrElse {
                _feedback.send(AppToastEvent.error(R.string.toast_settings_save_error))
                return@launch
            }

            // Confirmer uniquement une vraie modification. Cela évite aussi un
            // replanification WorkManager inutile si un composant renvoie sa valeur actuelle.
            if (updated == previous) return@launch

            // Comme pour le widget, la planification est best-effort : le réglage
            // reste enregistré. En cas d'échec, l'utilisateur doit toutefois savoir
            // que la prise en compte système est différée au prochain démarrage.
            val schedulingFailure = runCatching {
                WeatherNotificationScheduler.reschedule(
                    appContext,
                    updated,
                    kickAlertsImmediately = shouldKickAlertsImmediately(previous, updated)
                )
            }.exceptionOrNull()

            if (schedulingFailure != null) {
                android.util.Log.w(
                    "MeteoCompare/Notif",
                    "Unable to reschedule notifications",
                    schedulingFailure
                )
                _feedback.send(AppToastEvent.warning(R.string.toast_notifications_schedule_warning))
            } else {
                _feedback.send(successFeedback(previous, updated, favorites))
            }
        }
    }

    /**
     * Un contrôle d'alertes immédiat peut consommer du réseau si le cache météo
     * est périmé. Il n'a de sens que lorsqu'une nouvelle alerte devient possible
     * ou qu'une nouvelle ville entre dans le périmètre. Changer l'heure du résumé
     * quotidien, désactiver un type d'alerte ou retirer une ville ne doit pas
     * provoquer un fetch météo supplémentaire.
     */
    private fun shouldKickAlertsImmediately(
        previous: NotificationSettings,
        updated: NotificationSettings
    ): Boolean {
        if (!updated.alertsEnabled || updated.cityIds.isEmpty()) return false
        return (!previous.alertsEnabled && updated.alertsEnabled) ||
            (!previous.divergenceAlertsEnabled && updated.divergenceAlertsEnabled) ||
            (!previous.forecastChangeAlertsEnabled && updated.forecastChangeAlertsEnabled) ||
            (updated.cityIds - previous.cityIds).isNotEmpty()
    }

    /** Évite les écritures DataStore, toasts et side-effects pour un choix identique. */
    private suspend fun <T> updatePreferenceIfChanged(
        current: suspend () -> T,
        target: T,
        persist: suspend () -> Unit
    ): PreferenceUpdateResult = runSuspendCatching {
        if (current() == target) {
            PreferenceUpdateResult.UNCHANGED
        } else {
            persist()
            PreferenceUpdateResult.SAVED
        }
    }.getOrElse { PreferenceUpdateResult.FAILED }

    /**
     * L'écriture DataStore est le résultat métier. La propagation immédiate au
     * widget est best-effort : une panne WorkManager ne doit pas faire croire
     * que le réglage n'a pas été enregistré. Le prochain tick le relira.
     */
    private fun triggerWidgetRefreshSafely(): Boolean = runCatching {
        WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
    }.fold(
        onSuccess = { true },
        onFailure = { error ->
            android.util.Log.w(
                "MeteoCompare/Widget",
                "Unable to propagate settings to widgets immediately",
                error
            )
            false
        }
    )
}
