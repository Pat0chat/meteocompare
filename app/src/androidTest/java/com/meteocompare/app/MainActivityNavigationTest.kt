package com.meteocompare.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test d'intégration de démonstration avec Hilt.
 *
 * Cette classe lance la vraie [MainActivity] avec tous les bindings Hilt
 * réels (Retrofit, Room, DataStore). C'est utile pour :
 *   - vérifier que la DI est correctement câblée (ce test échoue à la
 *     compilation si un binding manque)
 *   - tester des flux end-to-end avec navigation réelle
 *
 * Pour mocker des dépendances (ex: MockWebServer pour Open-Meteo), utilise
 * `@UninstallModules(NetworkModule::class)` + un module de test annoté avec
 * `@TestInstallIn(replaces = [NetworkModule::class], components = [SingletonComponent::class])`.
 * Exemple (non implémenté ici) :
 *
 * ```
 * @TestInstallIn(replaces = [NetworkModule::class], components = [SingletonComponent::class])
 * @Module
 * object TestNetworkModule {
 *     @Provides @Singleton @ForecastRetrofit
 *     fun provideForecastRetrofit(): Retrofit = Retrofit.Builder()
 *         .baseUrl(mockWebServer.url("/"))
 *         .addConverterFactory(json.asConverterFactory(...))
 *         .build()
 * }
 * ```
 */
@HiltAndroidTest
class MainActivityNavigationTest {

    // Ordre important : Hilt d'abord (order=0), ce qui garantit l'injection
    // disponible avant que la Compose rule lance l'activité.
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun app_launches_on_city_list_screen() {
        // Le titre de la top app bar est "MeteoCompare" sur l'écran liste.
        composeRule.onNodeWithText("MeteoCompare").assertExists()
    }

    @Test
    fun empty_state_visible_on_fresh_install() {
        // À l'install les favoris sont vides → empty state affiché.
        // Note : ce test suppose qu'aucune migration ni seed initial ne
        // pré-remplit le DataStore. Si on ajoutait des villes par défaut
        // un jour, ce test devrait s'adapter.
        composeRule.onNodeWithText("Aucune ville en favoris").assertExists()
    }
}
