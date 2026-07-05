package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests unitaires du contrat de [weatherBadgeFor].
 *
 * On teste chaque famille météo et les 4 combinaisons de disponibilité des
 * extras (les deux fournis / probability seule / cloud seule / rien). Sans
 * ces cas, une régression du type "on affiche un badge cloud sur une pluie"
 * passerait entre les mailles.
 */
class WeatherCellBadgesTest {

    // ─── Familles pluie : badge = probabilité de pluie ─────────────────────

    /**
     * Data-driven : toutes les familles où le badge attendu est la probabilité
     * de pluie. Utiliser un array plutôt qu'un test par famille garde le fichier
     * compact tout en garantissant qu'un ajout de famille (ex : nouvelle
     * famille "GRÊLE") force un choix explicite ici — la compilation échoue
     * sur `when` non exhaustif dans [weatherBadgeFor] si on ne fait rien.
     */
    private val precipFamilies = listOf(
        WeatherCondition.RAIN,
        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN_SHOWERS,
        WeatherCondition.THUNDERSTORM,
        WeatherCondition.FREEZING_RAIN,
        WeatherCondition.SNOW,
        WeatherCondition.SNOW_SHOWERS
    )

    @Test
    fun `precip families show precipProbability badge when available`() {
        precipFamilies.forEach { condition ->
            assertEquals(
                "Family $condition should show precip probability",
                "60%",
                weatherBadgeFor(
                    condition = condition,
                    precipProbability = 60,
                    cloudCover = 90  // ignored for precip families
                )
            )
        }
    }

    @Test
    fun `precip families ignore cloudCover and prefer precipProbability`() {
        // Sanity : si le modèle fournit à la fois prob ET cloud, on doit prendre
        // prob pour la famille pluie. Sinon un bug de priorité afficherait la
        // cloud cover sur une icône pluie, ce qui n'a aucun sens sémantique.
        val badge = weatherBadgeFor(
            condition = WeatherCondition.RAIN,
            precipProbability = 40,
            cloudCover = 95
        )
        assertEquals("40%", badge)  // pas 95%
    }

    @Test
    fun `precip families return null when precipProbability missing`() {
        // Cache pré-feature ou modèle sans la variable : on n'affiche PAS de
        // badge, même si cloud cover est dispo. Éviter d'afficher un chiffre
        // "faussement associé" à la mauvaise sémantique.
        precipFamilies.forEach { condition ->
            assertNull(
                "Family $condition without prob should return null",
                weatherBadgeFor(
                    condition = condition,
                    precipProbability = null,
                    cloudCover = 80  // dispo mais ignoré pour les familles pluie
                )
            )
        }
    }

    // ─── Familles nuageuses : badge = cloud cover ──────────────────────────

    private val cloudFamilies = listOf(
        WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.OVERCAST
    )

    @Test
    fun `cloud families show cloudCover badge when available`() {
        cloudFamilies.forEach { condition ->
            assertEquals(
                "Family $condition should show cloud cover",
                "75%",
                weatherBadgeFor(
                    condition = condition,
                    precipProbability = 30, // ignored for cloud families
                    cloudCover = 75
                )
            )
        }
    }

    @Test
    fun `cloud families ignore precipProbability and prefer cloudCover`() {
        val badge = weatherBadgeFor(
            condition = WeatherCondition.OVERCAST,
            precipProbability = 50,
            cloudCover = 90
        )
        assertEquals("90%", badge)  // pas 50%
    }

    @Test
    fun `cloud families return null when cloudCover missing`() {
        cloudFamilies.forEach { condition ->
            assertNull(
                "Family $condition without cloudCover should return null",
                weatherBadgeFor(
                    condition = condition,
                    precipProbability = 40,  // dispo mais ignoré pour cloudy
                    cloudCover = null
                )
            )
        }
    }

    // ─── Familles neutres : pas de badge ───────────────────────────────────

    /**
     * Ces familles n'ont pas de badge, quelle que soit la valeur des extras.
     * Un badge sur une icône soleil serait perturbant ("60% de quoi ?"), on
     * respecte le principe "montrer un chiffre que si sa sémantique est
     * évidente au premier coup d'œil".
     */
    private val neutralFamilies = listOf(
        WeatherCondition.CLEAR,
        WeatherCondition.MAINLY_CLEAR,
        WeatherCondition.FOG,
        WeatherCondition.UNKNOWN
    )

    @Test
    fun `neutral families never show any badge even with values`() {
        neutralFamilies.forEach { condition ->
            assertNull(
                "Family $condition should never show a badge",
                weatherBadgeFor(
                    condition = condition,
                    precipProbability = 80,
                    cloudCover = 50
                )
            )
        }
    }

    // ─── Edge case : les deux extras absents ───────────────────────────────

    @Test
    fun `all null extras always return null regardless of family`() {
        // Blanket safety : cache antérieur à toutes les features → aucun badge.
        (precipFamilies + cloudFamilies + neutralFamilies).forEach { condition ->
            assertNull(
                "Family $condition with no extras should return null",
                weatherBadgeFor(
                    condition = condition,
                    precipProbability = null,
                    cloudCover = null
                )
            )
        }
    }

    // ─── Formatage : caractère "%" collé au nombre ─────────────────────────

    @Test
    fun `badge format uses percent sign glued to the number`() {
        // Regression : format "%d%%" ou " %" ferait dépasser la cellule de 60dp
        // ou donnerait un espace visuel étrange sous l'icône. On veut "60%"
        // strict, pas "60 %" ni "60 pourcent".
        assertEquals(
            "0%",
            weatherBadgeFor(WeatherCondition.RAIN, precipProbability = 0, cloudCover = null)
        )
        assertEquals(
            "100%",
            weatherBadgeFor(WeatherCondition.OVERCAST, precipProbability = null, cloudCover = 100)
        )
    }
}
