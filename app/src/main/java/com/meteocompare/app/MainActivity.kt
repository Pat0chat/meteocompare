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
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.ui.navigation.AppNavHost
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /**
     * Applique la locale persistée AVANT que les ressources soient résolues.
     *
     * Pourquoi pas AppCompatDelegate.getApplicationLocales() ?
     * - Ça ne fonctionnait pas fiablement sans AppCompatActivity comme parent.
     *   Le timing entre setApplicationLocales() et le recreate() faisait que
     *   attachBaseContext lisait parfois l'ancienne valeur — race condition
     *   non-déterministe selon le device et la version Android.
     * - AppCompatDelegate stocke en interne via le service
     *   AppLocalesMetadataHolderService, mais l'API getApplicationLocales()
     *   peut renvoyer une valeur en cache pas encore alignée avec ce qui vient
     *   d'être set.
     *
     * Solution : on stocke notre propre langue dans un SharedPreferences dédié
     * (commit() synchrone), et on lit ICI synchronement. AppCompatDelegate est
     * appelé en parallèle pour l'intégration système Android 13+ (per-app
     * language dans Settings > Languages), mais ce n'est plus notre source de
     * vérité.
     */
    override fun attachBaseContext(newBase: Context) {
        val tag = newBase
            .getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)
            .getString(LOCALE_KEY, null)
        val updatedBase = if (tag.isNullOrEmpty()) {
            // Pas de préférence → suit la locale système. C'est aussi le cas
            // par défaut pour LanguagePreference.SYSTEM.
            newBase
        } else {
            val locale = Locale.forLanguageTag(tag)
            // Locale.setDefault est important : certaines APIs (DateTimeFormatter
            // créés via Locale.getDefault, NumberFormat, etc.) lisent depuis
            // le default JVM-wide, pas depuis Configuration. Sans ça, les dates
            // resteraient sur la locale système même si les R.string changent.
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
            }
            newBase.createConfigurationContext(config)
        }
        super.attachBaseContext(updatedBase)
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

    companion object {
        /** Nom du fichier SharedPreferences dédié à la persistance de la locale. */
        const val LOCALE_PREFS = "meteocompare_locale_prefs"
        /** Clé : BCP47 tag (ex: "fr", "en"). Null/vide = suivre la locale système. */
        const val LOCALE_KEY = "language_tag"
    }
}
