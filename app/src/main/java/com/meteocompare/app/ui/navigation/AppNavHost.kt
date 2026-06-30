package com.meteocompare.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meteocompare.app.ui.citydetail.CityDetailScreen
import com.meteocompare.app.ui.citydetail.confidence.ConfidenceExplanationScreen
import com.meteocompare.app.ui.citylist.CityListScreen
import com.meteocompare.app.ui.settings.SettingsScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Destinations.CITY_LIST
    ) {
        composable(Destinations.CITY_LIST) {
            CityListScreen(
                onCityClick = { cityId ->
                    navController.navigate(Destinations.cityDetail(cityId))
                },
                onSettingsClick = {
                    navController.navigate(Destinations.SETTINGS)
                }
            )
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Destinations.CITY_DETAIL,
            arguments = listOf(navArgument(Destinations.CITY_DETAIL_ARG) {
                type = NavType.StringType
            })
        ) { backStackEntry ->
            val cityId = backStackEntry.arguments?.getString(Destinations.CITY_DETAIL_ARG)
                ?: return@composable
            CityDetailScreen(
                cityId = cityId,
                onBack = { navController.popBackStack() },
                onConfidenceClick = { isoDate ->
                    navController.navigate(Destinations.confidence(cityId, isoDate))
                }
            )
        }

        // ─── "Pourquoi cette confiance ?" ──────────────────────────────────
        // Sa propre route plutôt qu'un BottomSheet : c'est un contenu long
        // (header + 4 variables × 5-8 modèles + section éducative). Un sheet
        // mangerait l'écran et obligerait à scroller dans un demi-écran. Une
        // page navigable laisse aussi la possibilité d'un deep link futur.
        composable(
            route = Destinations.CONFIDENCE,
            arguments = listOf(
                navArgument(Destinations.CITY_DETAIL_ARG) { type = NavType.StringType },
                navArgument(Destinations.CONFIDENCE_DATE_ARG) { type = NavType.StringType }
            )
        ) {
            ConfidenceExplanationScreen(onBack = { navController.popBackStack() })
        }
    }
}
