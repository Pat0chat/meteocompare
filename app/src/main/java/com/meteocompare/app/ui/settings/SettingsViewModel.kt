package com.meteocompare.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.data.worker.BiasRefreshScheduler
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

    private val modelUpdateMutex = Mutex()

    val enabledModels: StateFlow<Set<WeatherModel>> = prefs.observeEnabledModels()
        .map { it.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherModel.MVP_SELECTION.toSet()
        )

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

    fun onModelToggled(model: WeatherModel, enabled: Boolean) {
        viewModelScope.launch {
            // Les taps peuvent arriver plus vite que la réémission DataStore.
            // On sérialise donc les mutations et on relit la source de vérité
            // dans la section critique, sinon deux toggles rapprochés peuvent
            // se réécrire mutuellement à partir d'un StateFlow encore ancien.
            modelUpdateMutex.withLock {
                val current = prefs.observeEnabledModels().first().toSet()
                val next = if (enabled) current + model else current - model
                if (next.isNotEmpty()) {
                    prefs.setEnabledModels(next.toList())
                    // Le widget lit la liste des modèles activés à chaque
                    // loadWidgetData. Sans ce trigger, il faudrait attendre le
                    // prochain tick 15 min pour que le changement se propage —
                    // frustrant pour l'utilisateur qui vient de faire un choix
                    // explicite. On force un tick immédiat pour re-fetcher avec
                    // le nouveau jeu de modèles tout de suite.
                    //
                    // Les demandes immédiates sont dédupliquées sous un nom
                    // WorkManager unique : plusieurs toggles rapides remplacent
                    // le tick précédent au lieu d'empiler des workers.
                    WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
                }
            }
        }
    }

    /**
     * Demande un cycle exceptionnel de collecte des biais. Le scheduler
     * conserve les contraintes réseau/batterie, le mutex global et la
     * déduplication WorkManager ; ce bouton ne modifie pas la cadence
     * quotidienne normale.
     */
    fun onBiasRefreshRequested() {
        BiasRefreshScheduler.triggerManualRefresh(appContext)
    }

    fun onThemeSelected(preference: ThemePreference) {
        viewModelScope.launch {
            prefs.setThemePreference(preference)
        }
    }

    /**
     * Persiste la langue dans l'unique stockage canonique. Cette fonction est
     * suspendue afin que l'écran puisse attendre la fin de l'écriture avant
     * `Activity.recreate()` et éviter toute course avec attachBaseContext().
     */
    suspend fun onLanguageSelected(preference: LanguagePreference) {
        prefs.setLanguagePreference(preference)
    }

    /**
     * Persiste le nouvel intervalle de rafraîchissement et propage
     * immédiatement le changement au widget.
     *
     * ─── Ce qui a changé vs version précédente ──────────────────────────
     * Avant : on re-programmait le worker WorkManager avec la nouvelle
     * cadence. Ça avait deux inconvénients :
     *   - Coupler la fréquence du tick d'affichage à un choix qui
     *     concerne le fetch réseau. Résultat : en HOURS_3, les labels
     *     d'heure du widget ne shiftaient qu'une fois toutes les 3h,
     *     alors que l'utilisateur voulait juste "moins de fetch".
     *   - Recréer un job périodique à chaque toggle utilisateur.
     *
     * Maintenant : la cadence tick est fixe (15 min), la RefreshInterval
     * sert UNIQUEMENT de seuil `maxCacheAgeMs` lu dynamiquement par
     * loadWidgetData à chaque tick. Il suffit donc de forcer un tick
     * immédiat pour que la nouvelle valeur soit prise en compte tout de
     * suite (sinon jusqu'à 15 min de latence).
     *
     * ─── Pourquoi côté VM et pas repository ? ───────────────────────────
     * Le repository est un pur data holder ; il ne connaît pas WorkManager.
     * On garde ce couplage explicit du côté de la VM (couche présentation)
     * plutôt que d'introduire une dépendance repository → WorkManager qui
     * casserait la testabilité pure du DataStore layer.
     *
     * Note : on trigger APRÈS que le DataStore ait persisté la valeur,
     * sinon le tick qu'on vient de forcer lirait l'ancienne valeur pour
     * le seuil de fraîcheur cache.
     */
    fun onRefreshIntervalSelected(interval: RefreshInterval) {
        viewModelScope.launch {
            prefs.setRefreshInterval(interval)
            WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
        }
    }


    /**
     * Change uniquement la stratégie de centrale : aucune requête réseau n'est
     * nécessaire. Le widget est toutefois rafraîchi immédiatement pour relire
     * la préférence et recalculer ses valeurs depuis le cache partagé.
     */
    fun onForecastEngineSelected(engine: ForecastEngine) {
        viewModelScope.launch {
            prefs.setForecastEngine(engine)
            WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
        }
    }
}
