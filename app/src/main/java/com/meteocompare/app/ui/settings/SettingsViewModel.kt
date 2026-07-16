package com.meteocompare.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

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

    fun onModelToggled(model: WeatherModel, enabled: Boolean) {
        viewModelScope.launch {
            val current = enabledModels.value
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

    fun onThemeSelected(preference: ThemePreference) {
        viewModelScope.launch {
            prefs.setThemePreference(preference)
        }
    }

    /**
     * Persiste la préférence de langue dans DataStore.
     *
     * ⚠ L'application effective de la locale (AppCompatDelegate.setApplicationLocales
     * + Activity.recreate) est faite côté Composable, SYNCHRONEMENT, AVANT que
     * l'Activity ne soit recréée. Sinon on a un race condition :
     *
     *   - viewModelScope.launch { ... } est async (coroutine sur Dispatchers.Main)
     *   - Si le Composable appelle recreate() juste après onLanguageSelected(),
     *     la coroutine de la VM n'a pas encore exécuté setApplicationLocales()
     *   - Donc attachBaseContext() lit l'ANCIENNE locale persistée par AppCompat
     *   - Résultat : aucun changement visible
     *
     * Solution : on découple. DataStore est purement pour notre UI (état du
     * SegmentedButton). AppCompat est la source de vérité pour la locale
     * effective, et son appel doit être synchrone côté UI.
     */
    fun onLanguageSelected(preference: LanguagePreference) {
        viewModelScope.launch {
            prefs.setLanguagePreference(preference)
        }
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
}
