package com.meteocompare.app

import android.os.Bundle
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

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
