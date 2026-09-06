package com.meteocompare.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meteocompare.app.R
import com.meteocompare.app.ui.citydetail.CityDetailScreen
import com.meteocompare.app.ui.citydetail.confidence.ConfidenceExplanationScreen
import com.meteocompare.app.ui.citylist.CityListScreen
import com.meteocompare.app.ui.citylist.CityListViewModel
import com.meteocompare.app.ui.enginecomparison.EngineComparisonScreen
import com.meteocompare.app.ui.help.HowItWorksScreen
import com.meteocompare.app.ui.settings.SettingsScreen

/** Largeur Material 3 « expanded », adaptée à deux volets réellement lisibles. */
internal val TABLET_TWO_PANE_MIN_WIDTH = 840.dp

private val TABLET_LIST_PANE_MIN_WIDTH = 340.dp
private val TABLET_LIST_PANE_MAX_WIDTH = 420.dp

internal const val TAG_TABLET_LAYOUT = "tablet_master_detail"
internal const val TAG_TABLET_LIST_PANE = "tablet_city_list_pane"
internal const val TAG_TABLET_DETAIL_PANE = "tablet_city_detail_pane"
internal const val TAG_TABLET_DETAIL_PLACEHOLDER = "tablet_city_detail_placeholder"

internal fun shouldUseTabletLayout(availableWidth: Dp): Boolean =
    availableWidth >= TABLET_TWO_PANE_MIN_WIDTH

internal fun tabletListPaneWidth(availableWidth: Dp): Dp =
    (availableWidth * 0.34f).coerceIn(
        minimumValue = TABLET_LIST_PANE_MIN_WIDTH,
        maximumValue = TABLET_LIST_PANE_MAX_WIDTH
    )

internal fun resolveSelectedCityId(
    currentCityId: String?,
    availableCityIds: List<String>
): String? = currentCityId
    ?.takeIf { it in availableCityIds }
    ?: availableCityIds.firstOrNull()

/**
 * Navigation adaptative : parcours plein écran sur téléphone, maître-détail sur
 * les fenêtres expanded (tablette, grand pliable ou fenêtre redimensionnée).
 */
@Composable
fun AppNavHost() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (shouldUseTabletLayout(maxWidth)) {
            TabletAppNavHost()
        } else {
            PhoneAppNavHost()
        }
    }
}

@Composable
private fun PhoneAppNavHost() {
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
                },
                onHelpClick = {
                    navController.navigate(Destinations.HELP)
                }
            )
        }

        commonFullScreenDestinations(navController)
        detailDestinations(
            navController = navController,
            showDetailBackButton = true
        )
    }
}

@Composable
private fun TabletAppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Destinations.CITY_LIST
    ) {
        composable(Destinations.CITY_LIST) {
            TabletHomeScreen(
                onSettingsClick = {
                    navController.navigate(Destinations.SETTINGS)
                },
                onHelpClick = {
                    navController.navigate(Destinations.HELP)
                }
            )
        }

        commonFullScreenDestinations(navController)
    }
}

/**
 * Le ViewModel de la liste reste attaché à la destination Home. Le détail a son
 * propre NavHost afin que les écrans « confiance » et « comparaison » restent
 * dans le volet droit sans faire disparaître la colonne de localités.
 */
@Composable
private fun TabletHomeScreen(
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit,
    cityListViewModel: CityListViewModel = hiltViewModel()
) {
    val listState by cityListViewModel.uiState.collectAsStateWithLifecycle()
    var selectedCityId by rememberSaveable { mutableStateOf<String?>(null) }
    val availableCityIds = listState.items.map { it.city.id }
    val activeCityId = resolveSelectedCityId(selectedCityId, availableCityIds)

    // Sélectionne la première ville au démarrage et bascule proprement sur la
    // suivante si la ville active est supprimée depuis son menu contextuel.
    LaunchedEffect(availableCityIds, activeCityId) {
        selectedCityId = activeCityId
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val listPaneWidth = tabletListPaneWidth(maxWidth)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_TABLET_LAYOUT)
        ) {
            Box(
                modifier = Modifier
                    .width(listPaneWidth)
                    .fillMaxHeight()
                    .testTag(TAG_TABLET_LIST_PANE)
            ) {
                CityListScreen(
                    onCityClick = { selectedCityId = it },
                    onSettingsClick = onSettingsClick,
                    onHelpClick = onHelpClick,
                    selectedCityId = activeCityId,
                    selectionEnabled = true,
                    viewModel = cityListViewModel
                )
            }

            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(TAG_TABLET_DETAIL_PANE)
            ) {
                val cityId = activeCityId
                if (cityId == null) {
                    TabletDetailPlaceholder()
                } else {
                    // Chaque localité possède ainsi une pile de navigation et
                    // un CityDetailViewModel ne contenant que son cityId.
                    key(cityId) {
                        TabletDetailNavHost(cityId = cityId)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletDetailNavHost(cityId: String) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Destinations.cityDetail(cityId),
        modifier = Modifier.fillMaxSize()
    ) {
        detailDestinations(
            navController = navController,
            showDetailBackButton = false,
            initialCityId = cityId
        )
    }
}

@Composable
private fun TabletDetailPlaceholder() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_TABLET_DETAIL_PLACEHOLDER),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationCity,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(18.dp)
                            .size(34.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.tablet_detail_placeholder_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.tablet_detail_placeholder_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun NavGraphBuilder.commonFullScreenDestinations(navController: NavHostController) {
    composable(Destinations.HELP) {
        HowItWorksScreen(onBack = { navController.popBackStack() })
    }

    composable(Destinations.SETTINGS) {
        SettingsScreen(onBack = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.detailDestinations(
    navController: NavHostController,
    showDetailBackButton: Boolean,
    initialCityId: String? = null
) {
    val detailRoute = initialCityId
        ?.let(Destinations::cityDetail)
        ?: Destinations.CITY_DETAIL

    composable(
        route = detailRoute,
        arguments = listOf(navArgument(Destinations.CITY_DETAIL_ARG) {
            type = NavType.StringType
            initialCityId?.let { defaultValue = it }
        })
    ) { backStackEntry ->
        val cityId = backStackEntry.arguments?.getString(Destinations.CITY_DETAIL_ARG)
            ?: return@composable
        CityDetailScreen(
            onBack = { navController.popBackStack() },
            showBackButton = showDetailBackButton,
            onConfidenceClick = { isoDate ->
                navController.navigate(Destinations.confidence(cityId, isoDate))
            },
            onEngineComparisonClick = {
                navController.navigate(Destinations.engineComparison(cityId))
            }
        )
    }

    composable(
        route = Destinations.ENGINE_COMPARISON,
        arguments = listOf(navArgument(Destinations.CITY_DETAIL_ARG) {
            type = NavType.StringType
        })
    ) {
        EngineComparisonScreen(onBack = { navController.popBackStack() })
    }

    // « Pourquoi cette convergence ? » reste une page dédiée : son contenu
    // est long et la navigation locale la conserve dans le volet de détail.
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
