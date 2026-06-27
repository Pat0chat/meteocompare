package com.meteocompare.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.ui.navigation.AppNavHost
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /**
     * Applique la locale persistée par AppCompat AVANT que les ressources soient
     * résolues. C'est le seul point d'entrée fiable pour ça sur API < 33.
     *
     * AppCompatDelegate.setApplicationLocales() persiste le choix mais ne le
     * propage pas automatiquement aux non-AppCompatActivity (notre cas — on
     * étend ComponentActivity pour Compose). Sans cet override, le toggle de
     * langue ne change RIEN à l'affichage : DataStore est mis à jour, AppCompat
     * persiste, mais le Context utilisé par stringResource() reste sur la locale
     * système.
     *
     * Cette méthode est appelée par le framework à chaque création d'Activity,
     * y compris après un recreate(). Donc le flow est :
     *   1. User tape "English" dans settings
     *   2. setApplicationLocales(en) → AppCompat persiste
     *   3. SettingsScreen call recreate()
     *   4. Framework recrée MainActivity
     *   5. attachBaseContext() lit la locale persistée et l'applique au Context
     *   6. Toutes les R.string.xxx sont résolues en anglais ✓
     */
    override fun attachBaseContext(newBase: Context) {
        val locales = AppCompatDelegate.getApplicationLocales()
        val updatedBase = if (locales.isEmpty) {
            newBase
        } else {
            val config = Configuration(newBase.resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // API 24+ : on peut passer un LocaleList complet pour gérer
                // les fallbacks. Mais on n'a qu'une locale dans notre prefs,
                // donc c'est en pratique équivalent à setLocale(locales[0]).
                val javaLocales = (0 until locales.size()).mapNotNull { locales[it] }.toTypedArray()
                config.setLocales(LocaleList(*javaLocales))
            } else {
                @Suppress("DEPRECATION")
                config.locale = locales[0]
            }
            newBase.createConfigurationContext(config)
        }
        super.attachBaseContext(updatedBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() DOIT être appelé AVANT super.onCreate() —
        // c'est ce qui hook le SplashScreen API dans la fenêtre. Sans ce
        // call, le theme Theme.MeteoCompare.Splash s'appliquerait mais ne
        // transitionnerait jamais vers Theme.MeteoCompare → écran figé.
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
