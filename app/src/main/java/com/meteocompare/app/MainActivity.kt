package com.meteocompare.app

import androidx.compose.runtime.CompositionLocalProvider
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnits

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.ui.navigation.AppNavHost
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import com.meteocompare.app.data.repository.CurrentLocationController
import javax.inject.Inject
import kotlinx.coroutines.Job
import com.meteocompare.app.widget.WidgetReceivers
import com.meteocompare.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    @Inject lateinit var currentLocation: CurrentLocationController
    private var foregroundLocationJob: Job? = null

    /**
     * Applique la locale persistée AVANT que les ressources soient résolues.
     *
     * Voir le docblock de [applyPersistedLocale] pour l'historique complet
     * (pourquoi pas AppCompatDelegate, quelle source de vérité, etc.).
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyPersistedLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
            val unitSystem by viewModel.unitSystem.collectAsStateWithLifecycle()
            val clockFormat by viewModel.clockFormatPreference.collectAsStateWithLifecycle()
            CompositionLocalProvider(
                LocalWeatherUnits provides WeatherUnits(unitSystem),
                LocalClockFormatPreference provides clockFormat
            ) {
                MeteoCompareTheme(themePreference = themePreference) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        foregroundLocationJob = lifecycleScope.launch {
            // No location polling or acquisition from widget workers/background tasks.
            currentLocation.refreshIfEnabled()
        }
        // Chaque retour réel au premier plan est une occasion de rattrapage
        // pour les launchers OEM qui ont différé les travaux en arrière-plan.
        // Le travail porte un nom unique et le garde évite tout enqueue quand
        // aucun widget n'est posé. Le rattrapage est volontairement NON forcé :
        // le worker conserve son garde de 15 minutes afin qu'un simple aller-
        // retour app ↔ launcher ne reconstruise pas le widget en boucle.
        lifecycleScope.launch(Dispatchers.IO) {
            val hasWidgets = runCatching {
                WidgetReceivers.anyAlive(
                    applicationContext,
                    AppWidgetManager.getInstance(applicationContext)
                )
            }.getOrElse { error ->
                android.util.Log.w(
                    "MeteoCompare/Widget",
                    "Unable to inspect installed widgets on app start",
                    error
                )
                false
            }
            if (hasWidgets) {
                runCatching {
                    WidgetRefreshScheduler.schedule(applicationContext)
                    WidgetRefreshScheduler.triggerCatchUpRefresh(applicationContext)
                }.onFailure { error ->
                    // Un WorkManager temporairement indisponible ne doit jamais
                    // faire échouer la reprise de l'activité principale.
                    android.util.Log.w(
                        "MeteoCompare/Widget",
                        "Unable to repair widget refresh on app start",
                        error
                    )
                }
            }
        }
    }

    override fun onStop() {
        foregroundLocationJob?.cancel()
        foregroundLocationJob = null
        super.onStop()
    }

}
