package com.meteocompare.app.domain.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Tests de [SolarTimes] contre des valeurs de référence connues.
 *
 * ─── Choix des points de calibration ─────────────────────────────────────
 * Villes couvrant :
 *   - latitudes tempérées (Paris) — le use case dominant
 *   - solstices d'été et d'hiver — les extrêmes annuels de la formule
 *   - équinoxes — la ligne de base
 *   - basses latitudes (Singapour, quasi-équateur) — cas où la variation
 *     saisonnière est minime, teste la robustesse de la formule
 *   - hautes latitudes (Reykjavik) — proche du cas polaire dégénéré
 *   - hémisphère sud (Sydney) — teste la symétrie
 *
 * ─── Tolérance ───────────────────────────────────────────────────────────
 * ±2 minutes vs les valeurs officielles de la Solar Calculator NOAA
 * (https://gml.noaa.gov/grad/solcalc/). C'est le budget de bruit du modèle
 * NOAA simplifié qu'on implémente : altitude ignorée, refraction fixée à
 * −50', équation du temps à 2 harmoniques.
 *
 * Une tolérance de 2 min ne fait perdre AUCUNE information utilisateur —
 * dire "lever à 6:12" vs "6:11" au bord d'un affichage HH:mm est
 * indiscernable.
 */
class SolarTimesTest {

    // Paris — laboratoire principal
    private val paris = 48.8566 to 2.3522
    private val parisZone = ZoneId.of("Europe/Paris")

    @Test
    fun `Paris solstice d'ete lever proche de 5h47`() {
        // Référence NOAA (source calc officiel) : 2024-06-21 → lever 5:47, coucher 21:57
        val times = SolarTimes.compute(
            latitude = paris.first,
            longitude = paris.second,
            date = LocalDate.of(2024, 6, 21),
            zone = parisZone
        )
        assertMinutesClose(LocalTime.of(5, 47), times.sunrise, tolMin = 2)
        assertMinutesClose(LocalTime.of(21, 57), times.sunset, tolMin = 2)
    }

    @Test
    fun `Paris solstice d'hiver jour court`() {
        // Référence NOAA : 2024-12-21 → lever 8:41, coucher 16:54
        val times = SolarTimes.compute(
            latitude = paris.first,
            longitude = paris.second,
            date = LocalDate.of(2024, 12, 21),
            zone = parisZone
        )
        assertMinutesClose(LocalTime.of(8, 41), times.sunrise, tolMin = 2)
        assertMinutesClose(LocalTime.of(16, 54), times.sunset, tolMin = 2)
    }

    @Test
    fun `Paris equinoxe printemps jour d'environ 12h`() {
        // Autour de l'équinoxe, jour ≈ nuit. Test de robustesse : la durée
        // doit être proche de 12h ± quelques minutes (déviation due à la
        // refraction atmosphérique — l'équinoxe "vrai" est décalé de ~1 semaine).
        val times = SolarTimes.compute(
            latitude = paris.first,
            longitude = paris.second,
            date = LocalDate.of(2024, 3, 20),
            zone = parisZone
        )
        assertNotNull(times.sunrise)
        assertNotNull(times.sunset)
        val durationMin = times.sunset!!.toSecondOfDay() / 60 -
            times.sunrise!!.toSecondOfDay() / 60
        // Durée d'un jour civil = 12h ± 10 min autour de l'équinoxe
        // (refraction + décalage équinoxe vrai vs date).
        assertTrue(
            "Durée du jour = $durationMin min, attendu ~720",
            abs(durationMin - 720) <= 15
        )
    }

    @Test
    fun `Singapour quasi-equateur jour d'environ 12h toute l'annee`() {
        // À 1.3°N, la variation jour/nuit sur l'année est <15 min. Test de
        // robustesse pour les basses latitudes (où sin(lat) → 0 dans la formule).
        val singapore = 1.3521 to 103.8198
        val zone = ZoneId.of("Asia/Singapore")

        val summer = SolarTimes.compute(
            singapore.first, singapore.second,
            LocalDate.of(2024, 6, 21), zone
        )
        val winter = SolarTimes.compute(
            singapore.first, singapore.second,
            LocalDate.of(2024, 12, 21), zone
        )

        val durationSummer = summer.sunset!!.toSecondOfDay() -
            summer.sunrise!!.toSecondOfDay()
        val durationWinter = winter.sunset!!.toSecondOfDay() -
            winter.sunrise!!.toSecondOfDay()

        // Sur l'équateur, les 2 durées doivent être quasi-identiques.
        val deltaMin = abs(durationSummer - durationWinter) / 60
        assertTrue(
            "Δ durée été/hiver à Singapour = $deltaMin min, attendu < 15",
            deltaMin < 15
        )
    }

    @Test
    fun `hemisphere sud Sydney symetrie inversee`() {
        // Sydney -33.87°S : le "solstice d'été local" est fin décembre.
        // Le jour doit être LONG en décembre et COURT en juin — opposé de Paris.
        val sydney = -33.8688 to 151.2093
        val zone = ZoneId.of("Australia/Sydney")

        val dec = SolarTimes.compute(sydney.first, sydney.second, LocalDate.of(2024, 12, 21), zone)
        val jun = SolarTimes.compute(sydney.first, sydney.second, LocalDate.of(2024, 6, 21), zone)

        val durationDec = dec.sunset!!.toSecondOfDay() - dec.sunrise!!.toSecondOfDay()
        val durationJun = jun.sunset!!.toSecondOfDay() - jun.sunrise!!.toSecondOfDay()

        // En décembre (été austral), jour long ; en juin (hiver austral), jour court.
        assertTrue(
            "Décembre à Sydney doit être + long que juin (${durationDec}s vs ${durationJun}s)",
            durationDec > durationJun
        )
    }

    @Test
    fun `nuit polaire au dela du cercle arctique en decembre`() {
        // Longyearbyen (Svalbard, 78°N) : entre novembre et janvier, le soleil
        // ne se lève pas. Test du cas dégénéré (cosHa hors [-1, 1]).
        val svalbard = 78.2232 to 15.6267
        val zone = ZoneId.of("Arctic/Longyearbyen")

        val times = SolarTimes.compute(
            svalbard.first, svalbard.second,
            LocalDate.of(2024, 12, 21), zone
        )
        assertNull("Nuit polaire : pas de lever attendu", times.sunrise)
        assertNull("Nuit polaire : pas de coucher attendu", times.sunset)
    }

    @Test
    fun `soleil de minuit au dela du cercle arctique en juin`() {
        // Même Longyearbyen mais en juin : le soleil ne se couche pas.
        val svalbard = 78.2232 to 15.6267
        val zone = ZoneId.of("Arctic/Longyearbyen")

        val times = SolarTimes.compute(
            svalbard.first, svalbard.second,
            LocalDate.of(2024, 6, 21), zone
        )
        assertNull("Soleil de minuit : pas de lever attendu", times.sunrise)
        assertNull("Soleil de minuit : pas de coucher attendu", times.sunset)
    }

    @Test
    fun `latitude hors bornes leve exception`() {
        try {
            SolarTimes.compute(91.0, 0.0, LocalDate.now(), parisZone)
            org.junit.Assert.fail("Latitude 91° devait lever")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("latitude"))
        }
    }

    @Test
    fun `longitude hors bornes leve exception`() {
        try {
            SolarTimes.compute(0.0, 181.0, LocalDate.now(), parisZone)
            org.junit.Assert.fail("Longitude 181° devait lever")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("longitude"))
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /** Assert que deux LocalTime sont à ±[tolMin] minutes l'un de l'autre. */
    private fun assertMinutesClose(expected: LocalTime, actual: LocalTime?, tolMin: Int) {
        assertNotNull("valeur inattendue: null", actual)
        val expectedMin = expected.toSecondOfDay() / 60
        val actualMin = actual!!.toSecondOfDay() / 60
        val delta = abs(expectedMin - actualMin)
        assertTrue(
            "Δ = $delta min entre $expected et $actual (toléré ±$tolMin)",
            delta <= tolMin
        )
    }
}
