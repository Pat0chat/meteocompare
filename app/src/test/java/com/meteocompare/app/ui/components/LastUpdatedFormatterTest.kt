package com.meteocompare.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Tests unitaires du contrat de [computeLastUpdatedPalier] et [refreshIntervalMsFor].
 *
 * On teste UNIQUEMENT la logique métier pure — pas [formatLastUpdated] qui est
 * un thin wrapper autour de [context.getString()]. Tester la couche de string
 * i18n demanderait Robolectric (overhead ~500ms/test) ou du mocking mockk avec
 * la trap vararg de Context.getString(int, Object...) qui casse `arg<Int>()`
 * en ClassCastException — pour zéro valeur ajoutée vs tester le pur.
 *
 * Découpage des cas :
 *   - Bornes exactes des paliers (59s, 60s, 3599s, 3600s, etc.)
 *   - Arrondi (troncation entière — jamais d'over-round)
 *   - Cas d'horloge défectueuse (fetchedAt dans le futur)
 *   - Contrat de fréquence de refresh (assez rapide au palier bas, économe
 *     au palier haut)
 */
class LastUpdatedFormatterTest {

    private val now: Instant = Instant.parse("2025-06-15T12:00:00Z")

    // ─── Palier JustNow (< 60 s) ───────────────────────────────────────────

    @Test
    fun `zero seconds ago maps to JustNow`() {
        assertEquals(
            LastUpdatedPalier.JustNow,
            computeLastUpdatedPalier(now, now)
        )
    }

    @Test
    fun `30 seconds ago maps to JustNow — no sub-minute precision`() {
        // On ne raffine pas en dessous de la minute : "il y a 30 s" apporterait
        // zéro information vs "à l'instant" et bruiterait le layout (label qui
        // change 2 fois par minute).
        assertEquals(
            LastUpdatedPalier.JustNow,
            computeLastUpdatedPalier(now.minusSeconds(30), now)
        )
    }

    @Test
    fun `59 seconds ago still maps to JustNow — upper edge of the palier`() {
        assertEquals(
            LastUpdatedPalier.JustNow,
            computeLastUpdatedPalier(now.minusSeconds(59), now)
        )
    }

    // ─── Palier Minutes (60 s à 59 min 59 s) ───────────────────────────────

    @Test
    fun `exactly 60 seconds ago bumps to Minutes(1) — lower edge`() {
        // La bascule doit se produire à 60 s pile, pas plus tard. Sinon on
        // aurait un instant furtif où "à l'instant" est déjà faux mais on ne
        // l'affiche pas encore.
        assertEquals(
            LastUpdatedPalier.Minutes(1),
            computeLastUpdatedPalier(now.minusSeconds(60), now)
        )
    }

    @Test
    fun `5 min 30 s ago floors to Minutes(5) — truncation, not rounding`() {
        // Troncation entière : on préfère "il y a 5 min" à "il y a 6 min"
        // quand il ne s'est écoulé que 5 pleines minutes. Éviter les
        // over-round est une règle d'or pour les libellés de fraîcheur.
        assertEquals(
            LastUpdatedPalier.Minutes(5),
            computeLastUpdatedPalier(
                now.minus(Duration.ofSeconds(5 * 60 + 30)),
                now
            )
        )
    }

    @Test
    fun `59 min 59 s ago still maps to Minutes(59) — upper edge`() {
        assertEquals(
            LastUpdatedPalier.Minutes(59),
            computeLastUpdatedPalier(
                now.minus(Duration.ofSeconds(59 * 60 + 59)),
                now
            )
        )
    }

    // ─── Palier Hours (1 h à 23 h 59 min 59 s) ─────────────────────────────

    @Test
    fun `exactly 60 minutes ago bumps to Hours(1) — lower edge`() {
        assertEquals(
            LastUpdatedPalier.Hours(1),
            computeLastUpdatedPalier(now.minus(Duration.ofMinutes(60)), now)
        )
    }

    @Test
    fun `3 h 45 min ago floors to Hours(3) — truncation preserved at hour scale`() {
        assertEquals(
            LastUpdatedPalier.Hours(3),
            computeLastUpdatedPalier(
                now.minus(Duration.ofMinutes(3 * 60 + 45)),
                now
            )
        )
    }

    @Test
    fun `23 h 59 min ago still maps to Hours(23) — upper edge`() {
        assertEquals(
            LastUpdatedPalier.Hours(23),
            computeLastUpdatedPalier(
                now.minus(Duration.ofMinutes(23 * 60 + 59)),
                now
            )
        )
    }

    // ─── Palier Days (≥ 24 h) ──────────────────────────────────────────────

    @Test
    fun `exactly 24 hours ago bumps to Days(1) — lower edge`() {
        assertEquals(
            LastUpdatedPalier.Days(1),
            computeLastUpdatedPalier(now.minus(Duration.ofHours(24)), now)
        )
    }

    @Test
    fun `3 days 12 hours ago floors to Days(3) — truncation at day scale`() {
        assertEquals(
            LastUpdatedPalier.Days(3),
            computeLastUpdatedPalier(
                now.minus(Duration.ofHours(3 * 24 + 12)),
                now
            )
        )
    }

    // ─── Défense contre les horloges défectueuses ──────────────────────────

    @Test
    fun `fetchedAt in the future clamps to JustNow instead of negative delta`() {
        // Cas rare mais réel : NTP corrige l'heure système en arrière juste
        // après un fetch → fetchedAt est dans le futur. On préfère "à l'instant"
        // qu'un "il y a -3 min" qui ferait paniquer un user attentif.
        assertEquals(
            LastUpdatedPalier.JustNow,
            computeLastUpdatedPalier(now.plusSeconds(180), now)
        )
    }

    @Test
    fun `fetchedAt far in the future still clamps to JustNow`() {
        // Cas extrême : plusieurs jours dans le futur. Comportement identique —
        // on ne bascule PAS vers un palier "Days" négatif ou trompeur.
        assertEquals(
            LastUpdatedPalier.JustNow,
            computeLastUpdatedPalier(now.plus(Duration.ofDays(5)), now)
        )
    }

    // ─── Délai jusqu'au prochain changement visible ─────────────────────

    @Test
    fun `next delay - JustNow attend exactement la minute`() {
        assertEquals(
            45_000L,
            nextLastUpdatedRefreshDelayMs(now.minusSeconds(15), now)
        )
    }

    @Test
    fun `next delay - Minutes attend la prochaine frontière minute`() {
        assertEquals(
            50_000L,
            nextLastUpdatedRefreshDelayMs(now.minusSeconds(70), now)
        )
    }

    @Test
    fun `next delay - Hours attend la prochaine frontière heure`() {
        assertEquals(
            1_800_000L,
            nextLastUpdatedRefreshDelayMs(now.minusSeconds(5_400), now)
        )
    }

    @Test
    fun `next delay - Days attend la prochaine frontière jour`() {
        assertEquals(
            43_200_000L,
            nextLastUpdatedRefreshDelayMs(now.minusSeconds(129_600), now)
        )
    }

    @Test
    fun `next delay - horloge reculée attend une minute sans boucle serrée`() {
        assertEquals(
            60_000L,
            nextLastUpdatedRefreshDelayMs(now.plusSeconds(120), now)
        )
    }

}
