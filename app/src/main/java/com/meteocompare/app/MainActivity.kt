package com.meteocompare.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.ui.navigation.AppNavHost
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import dagger.hilt.android.AndroidEntryPoint

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

}
