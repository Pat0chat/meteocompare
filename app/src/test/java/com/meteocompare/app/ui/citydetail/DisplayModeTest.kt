package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires du contrat de [DisplayMode.Saver].
 *
 * Objectif : garantir qu'un round-trip save → restore reste stable, et que la
 * restauration reste correcte si l'enum est réordonné ou si une valeur
 * inconnue arrive (bundle d'une version antérieure).
 */
class DisplayModeTest {

    // Un SaverScope minimal — le contrat du save lambda le prend en receiver,
    // mais on ne l'utilise pas dans notre implémentation.
    private val scope = SaverScope { true }

    @Test
    fun `save then restore returns the same mode - DAILY`() {
        val saver = DisplayMode.Saver
        val saved = with(saver) { scope.save(DisplayMode.DAILY) }
        assertEquals("DAILY", saved)
        val restored = saver.restore(saved!!)
        assertEquals(DisplayMode.DAILY, restored)
    }

    @Test
    fun `save then restore returns the same mode - HOURLY`() {
        val saver = DisplayMode.Saver
        val saved = with(saver) { scope.save(DisplayMode.HOURLY) }
        assertEquals("HOURLY", saved)
        val restored = saver.restore(saved!!)
        assertEquals(DisplayMode.HOURLY, restored)
    }

    @Test
    fun `restore unknown value falls back to DAILY (defensive)`() {
        // Simule un bundle stocké par une version antérieure avec une constante
        // qui n'existe plus. On préfère retomber sur DAILY (comportement
        // historique) plutôt que crasher.
        val restored = DisplayMode.Saver.restore("SOMETHING_ELSE")
        assertEquals(DisplayMode.DAILY, restored)
    }

    @Test
    fun `save uses name and not ordinal so reordering the enum stays safe`() {
        // Régression : si un jour on réordonne l'enum (HOURLY et DAILY swappés),
        // les bundles existants doivent continuer à restaurer la bonne valeur.
        // Le contrat "save = it.name" garantit ça, contrairement à "save = ordinal".
        val saved = with(DisplayMode.Saver) { scope.save(DisplayMode.HOURLY) }
        assertEquals("HOURLY", saved)  // pas "0", pas "1"
    }
}
