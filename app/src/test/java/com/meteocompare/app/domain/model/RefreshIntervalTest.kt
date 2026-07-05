package com.meteocompare.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * Tests unitaires de [RefreshInterval].
 *
 * Vérifient les contrats critiques :
 *  - `fromString` fait le round-trip identité pour toute valeur connue
 *  - `fromString` tombe sur [RefreshInterval.DEFAULT] pour valeurs inconnues
 *    ou null (protection contre les migrations DataStore malformées)
 *  - `millis` = valeur du Duration en millisecondes (invariant essentiel :
 *    utilisé comme `maxCacheAgeMs` dans le repository et comme cadence
 *    WorkManager — une erreur d'unité biaiserait tous les rafraîchissements)
 *  - `MANUAL.duration == Duration.ZERO` — invariant utilisé pour distinguer
 *    "annuler le worker" de "programmer avec cadence courte"
 */
class RefreshIntervalTest {

    @Test
    fun `fromString - round trip pour chaque valeur connue`() {
        RefreshInterval.entries.forEach { interval ->
            assertEquals(
                "fromString($interval.name) doit retourner $interval",
                interval,
                RefreshInterval.fromString(interval.name)
            )
        }
    }

    @Test
    fun `fromString - retourne DEFAULT sur null`() {
        // Cas typique : préférence pas encore écrite (première ouverture).
        // Doit tomber sur le défaut plutôt que crash.
        assertEquals(RefreshInterval.DEFAULT, RefreshInterval.fromString(null))
    }

    @Test
    fun `fromString - retourne DEFAULT sur chaîne inconnue`() {
        // Cas de migration : ancienne version avait "FIVE_MINUTES" par ex,
        // qu'on ne supporte plus. Tolérer plutôt que planter.
        assertEquals(RefreshInterval.DEFAULT, RefreshInterval.fromString("FIVE_MINUTES"))
        assertEquals(RefreshInterval.DEFAULT, RefreshInterval.fromString(""))
    }

    @Test
    fun `millis - Duration converti en millisecondes`() {
        // Les valeurs numériques exactes matter — un widget rafraîchi à
        // 15000 au lieu de 15 min (900_000 ms) épuiserait la batterie.
        assertEquals(15 * 60 * 1000L, RefreshInterval.MINUTES_15.millis)
        assertEquals(30 * 60 * 1000L, RefreshInterval.MINUTES_30.millis)
        assertEquals(60 * 60 * 1000L, RefreshInterval.HOUR_1.millis)
        assertEquals(3 * 60 * 60 * 1000L, RefreshInterval.HOURS_3.millis)
        assertEquals(6 * 60 * 60 * 1000L, RefreshInterval.HOURS_6.millis)
    }

    @Test
    fun `MANUAL - duration est ZERO`() {
        // Contrat : le scheduler détecte MANUAL par duration ZERO
        // (annuler le worker au lieu de programmer une cadence 0 qui
        // serait ambigüe). Ne pas changer sans mettre à jour le scheduler.
        assertEquals(Duration.ZERO, RefreshInterval.MANUAL.duration)
        assertEquals(0L, RefreshInterval.MANUAL.millis)
    }

    @Test
    fun `DEFAULT est HOUR_1`() {
        // Contrat produit : le défaut est calé sur la cadence de publication
        // des modèles Open-Meteo (~1h). Un changement ici change le comportement
        // par défaut pour TOUS les utilisateurs sur nouvelle install — doit
        // rester un choix conscient.
        assertEquals(RefreshInterval.HOUR_1, RefreshInterval.DEFAULT)
    }
}
