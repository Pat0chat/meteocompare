package com.meteocompare.app

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
import com.meteocompare.app.widget.WidgetReceivers
import com.meteocompare.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

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
            MeteoCompareTheme(themePreference = themePreference) {
                AppNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Chaque retour réel au premier plan est une occasion de rattrapage
        // pour les launchers OEM qui ont différé les travaux en arrière-plan.
        // Le travail porte un nom unique et le garde évite tout enqueue quand
        // aucun widget n'est posé.
        lifecycleScope.launch(Dispatchers.IO) {
            val hasWidgets = runCatching {
                WidgetReceivers.anyAlive(
                    applicationContext,
                    AppWidgetManager.getInstance(applicationContext)
                )
            }.getOrDefault(false)
            if (hasWidgets) {
                WidgetRefreshScheduler.schedule(applicationContext)
                WidgetRefreshScheduler.triggerImmediateRefresh(applicationContext)
            }
        }
    }

}
