package com.meteocompare.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants de l'enum [WeatherModel].
 *
 * Objectif : détecter tôt les régressions quand un nouveau modèle est ajouté
 * sans mettre à jour la palette de couleurs, sans respecter les conventions
 * de nommage d'apiKey, ou sans classer proprement famille/couverture.
 *
 * Choix éditoriaux verrouillés par ces tests :
 *   - Toutes les apiKey sont uniques (sinon deux modèles taperaient la même
 *     URL Open-Meteo — ambiguïté silencieuse côté API)
 *   - Toutes les displayName sont uniques (sinon deux lignes indistinguables
 *     dans la liste settings et les charts)
 *   - Les modèles France sont produits par Météo-France (invariant métier)
 *   - MVP_SELECTION reste raisonnable en taille (max 10 modèles pour limiter
 *     le volume de la réponse batched et le coût de mapping/cache)
 *   - MVP_SELECTION couvre au moins 1 modèle par grande catégorie de zone
 *     pour donner du sens à la comparaison inter-modèles dès le 1er lancement
 */
class WeatherModelInvariantsTest {

    @Test
    fun `apiKey unique pour chaque modèle`() {
        val all = WeatherModel.entries.map { it.apiKey }
        assertEquals(
            "Deux modèles partagent la même apiKey : ${all.groupingBy { it }
                .eachCount().filter { it.value > 1 }}",
            all.size,
            all.distinct().size
        )
    }

    @Test
    fun `displayName unique pour chaque modèle`() {
        val all = WeatherModel.entries.map { it.displayName }
        assertEquals(
            "Deux modèles partagent le même displayName : ${all.groupingBy { it }
                .eachCount().filter { it.value > 1 }}",
            all.size,
            all.distinct().size
        )
    }

    @Test
    fun `modèles France produits par Météo-France`() {
        WeatherModel.entries
            .filter { it.coverage == Coverage.FRANCE }
            .forEach {
                assertEquals(
                    "$it a coverage FRANCE mais family=${it.family}",
                    ModelFamily.METEO_FRANCE,
                    it.family
                )
            }
    }

    @Test
    fun `résolution positive pour chaque modèle`() {
        WeatherModel.entries.forEach {
            assertTrue(
                "$it a une résolution invalide : ${it.resolutionKm}",
                it.resolutionKm > 0.0
            )
        }
    }

    @Test
    fun `horizon prévision positif pour chaque modèle`() {
        WeatherModel.entries.forEach {
            assertTrue(
                "$it a un horizon invalide : ${it.maxForecastDays}",
                it.maxForecastDays > 0
            )
        }
    }

    @Test
    fun `MVP_SELECTION - taille raisonnable`() {
        // Borne haute : l'appel réseau reste batched, mais chaque modèle ajoute
        // des séries à télécharger, parser, stocker et agréger.
        assertTrue(
            "MVP_SELECTION est trop grosse : ${WeatherModel.MVP_SELECTION.size} modèles",
            WeatherModel.MVP_SELECTION.size in 3..10
        )
    }

    @Test
    fun `MVP_SELECTION - couvre au moins un modèle global`() {
        // Pour un user quelconque dans le monde, il faut au moins un modèle
        // qui couvre sa position (les régionaux ne fonctionnent que sur leur
        // zone). Un global assure une expérience minimale utilisable partout.
        assertTrue(
            "MVP_SELECTION doit contenir au moins un modèle global",
            WeatherModel.MVP_SELECTION.any { it.coverage == Coverage.GLOBAL }
        )
    }

    @Test
    fun `MVP_SELECTION - couvre au moins deux familles distinctes`() {
        // La comparaison inter-modèles n'a de sens qu'entre modèles produits
        // par des institutions différentes (diversité méthodologique). Un
        // MVP avec juste "AROME + ARPEGE" (Météo-France × 2) ferait de la
        // comparaison de deux variantes du même moteur, peu informatif.
        val families = WeatherModel.MVP_SELECTION.map { it.family }.distinct()
        assertTrue(
            "MVP_SELECTION doit couvrir au moins 2 familles distinctes, trouvé $families",
            families.size >= 2
        )
    }

    @Test
    fun `family displayName non vide`() {
        ModelFamily.entries.forEach {
            assertNotNull(it.displayName)
            assertTrue(it.displayName.isNotBlank())
        }
    }
    @Test
    fun `HRRR est classé États-Unis et non global`() {
        assertEquals(Coverage.UNITED_STATES, WeatherModel.HRRR_CONUS.coverage)
    }

    @Test
    fun `métadonnées modèles critiques restent cohérentes`() {
        assertEquals(15, WeatherModel.ECMWF.maxForecastDays)
        assertEquals(15, WeatherModel.ECMWF_AIFS.maxForecastDays)
        assertEquals(28.0, WeatherModel.ECMWF_AIFS.resolutionKm, 0.0)
        assertEquals(3, WeatherModel.KNMI_HARMONIE_EU.maxForecastDays)
        assertEquals(15.0, WeatherModel.BOM_ACCESS.resolutionKm, 0.0)
    }

}
