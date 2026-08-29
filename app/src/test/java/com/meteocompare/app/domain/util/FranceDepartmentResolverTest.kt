package com.meteocompare.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FranceDepartmentResolverTest {

    @Test
    fun `resout les departements metropolitains depuis le code postal`() {
        assertEquals("91", FranceDepartmentResolver.resolve(null, listOf("91190")))
        assertEquals("75", FranceDepartmentResolver.resolve(null, listOf("75004")))
    }

    @Test
    fun `resout la Corse depuis admin2 sans confondre 2A et 2B`() {
        assertEquals("2A", FranceDepartmentResolver.resolve("Corse-du-Sud", listOf("20000")))
        assertEquals("2B", FranceDepartmentResolver.resolve("Haute-Corse", listOf("20200")))
        assertNull(FranceDepartmentResolver.resolve(null, listOf("20000")))
    }

    @Test
    fun `resout les DROM sur trois chiffres`() {
        assertEquals("971", FranceDepartmentResolver.resolve(null, listOf("97100")))
        assertEquals("974", FranceDepartmentResolver.resolve("La Réunion", emptyList()))
        assertEquals("976", FranceDepartmentResolver.resolve("Mayotte", emptyList()))
    }
}
