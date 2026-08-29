package com.meteocompare.app.data.repository

import com.meteocompare.app.domain.model.City
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VigilanceCacheSharingTest {

    @Test
    fun `cache peut etre purge quand aucun favori ne reste`() {
        assertFalse(isVigilanceDepartmentStillUsed("75", emptyList()))
    }

    @Test
    fun `cache partage conserve si une autre ville du meme departement reste`() {
        val paris15 = city(id = "2", name = "Paris 15e", departmentCode = "75")

        assertTrue(isVigilanceDepartmentStillUsed("75", listOf(paris15)))
    }

    @Test
    fun `cache peut etre purge si les favoris francais restants sont dans dautres departements`() {
        val lyon = city(id = "2", name = "Lyon", departmentCode = "69")
        val lille = city(id = "3", name = "Lille", departmentCode = "59")

        assertFalse(isVigilanceDepartmentStillUsed("75", listOf(lyon, lille)))
    }

    @Test
    fun `favori francais legacy non resolu bloque la purge par prudence`() {
        val legacy = city(id = "2", name = "Ville legacy", departmentCode = null)

        assertTrue(isVigilanceDepartmentStillUsed("75", listOf(legacy)))
    }

    @Test
    fun `ville etrangere sans departement ne bloque pas la purge`() {
        val london = City(
            id = "3",
            name = "London",
            country = "United Kingdom",
            latitude = 51.5074,
            longitude = -0.1278,
            countryCode = "GB"
        )

        assertFalse(isVigilanceDepartmentStillUsed("75", listOf(london)))
    }

    @Test
    fun `comparaison du code departement est normalisee`() {
        val ajaccio = city(id = "4", name = "Ajaccio", departmentCode = "2a")

        assertTrue(isVigilanceDepartmentStillUsed(" 2A ", listOf(ajaccio)))
    }

    private fun city(
        id: String,
        name: String,
        departmentCode: String?
    ) = City(
        id = id,
        name = name,
        country = "France",
        latitude = 48.0,
        longitude = 2.0,
        countryCode = "FR",
        departmentCode = departmentCode
    )
}
