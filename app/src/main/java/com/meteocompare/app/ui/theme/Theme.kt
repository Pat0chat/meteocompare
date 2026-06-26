package com.meteocompare.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.meteocompare.app.domain.model.ThemePreference

private val DarkColorScheme = darkColorScheme(
    primary = Primary80,
    secondary = Secondary80,
    tertiary = Tertiary80
)

private val LightColorScheme = lightColorScheme(
    primary = Primary40,
    secondary = Secondary40,
    tertiary = Tertiary40
)

/**
 * Theme racine de l'app.
 *
 * Le `themePreference` est résolu en un booléen dark/light :
 *   - SYSTEM → suit l'OS via `isSystemInDarkTheme()`
 *   - LIGHT → force false
 *   - DARK → force true
 *
 * `isSystemInDarkTheme()` est composé même quand on l'ignore, ce qui permet
 * la recomposition automatique si le user change la pref de SYSTEM à LIGHT
 * (et inversement) en cours d'utilisation.
 */
@Composable
fun MeteoCompareTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    // Dynamic color (Material You) — disponible à partir d'Android 12.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // `statusBarColor` est déprécié depuis API 35 — Google attend qu'on
            // laisse la barre transparente et que le contenu (TopAppBar) la
            // remplisse en scrollant dessous. C'est le comportement par défaut
            // une fois `enableEdgeToEdge()` appelé dans MainActivity.
            //
            // On garde ici uniquement le contrôle de la teinte des icônes
            // système (heure, batterie, signal) : ces icônes doivent être
            // foncées quand l'arrière-plan est clair et inversement.
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
