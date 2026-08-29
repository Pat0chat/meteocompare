package com.meteocompare.app.data.mapper

import com.meteocompare.app.data.remote.dto.VigilanceIntervalDto
import com.meteocompare.app.data.remote.dto.VigilancePeriodDto
import com.meteocompare.app.data.remote.dto.VigilancePhenomenonDto
import com.meteocompare.app.data.remote.dto.VigilanceResponseDto
import com.meteocompare.app.domain.model.VigilanceColor
import com.meteocompare.app.domain.model.VigilancePhenomenon
import com.meteocompare.app.domain.model.VigilanceScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class VigilanceMapperTest {

    @Test
    fun `mappe couleurs phenomenes et intervalles du contrat Worker`() {
        val dto = VigilanceResponseDto(
            department = "91",
            periods = listOf(
                VigilancePeriodDto(
                    term = "J",
                    beginTime = "2026-08-29T06:00:00+02:00",
                    endTime = "2026-08-30T00:00:00+02:00",
                    maxColorId = 3,
                    departmentMaxColorId = 3,
                    phenomena = listOf(
                        VigilancePhenomenonDto(
                            id = "3",
                            maxColorId = 3,
                            intervals = listOf(
                                VigilanceIntervalDto(
                                    beginTime = "2026-08-29T14:00:00+02:00",
                                    endTime = "2026-08-29T21:00:00+02:00",
                                    colorId = 3,
                                    scope = "department"
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = requireNotNull(dto.toDomain(Instant.parse("2026-08-29T05:00:00Z")))
        val alert = result.activeAlerts.single()
        assertEquals("91", result.department)
        assertEquals(VigilancePhenomenon.THUNDERSTORMS, alert.phenomenon)
        assertEquals(VigilanceColor.ORANGE, alert.maxColor)
        assertEquals(VigilanceScope.DEPARTMENT, alert.intervals.single().scope)
    }

    @Test
    fun `retourne null si le Worker est non configure ou indisponible`() {
        assertNull(VigilanceResponseDto(configured = false, department = "91").toDomain(Instant.EPOCH))
        assertNull(VigilanceResponseDto(unavailable = true, department = "91").toDomain(Instant.EPOCH))
    }

    @Test
    fun `identifie la vigilance vagues submersion littorale`() {
        val dto = VigilanceResponseDto(
            department = "29",
            includeCoast = true,
            periods = listOf(
                VigilancePeriodDto(
                    maxColorId = 3,
                    coastMaxColorId = 3,
                    phenomena = listOf(
                        VigilancePhenomenonDto(
                            id = "9",
                            maxColorId = 3,
                            intervals = listOf(VigilanceIntervalDto(colorId = 3, scope = "coast"))
                        )
                    )
                )
            )
        )
        val result = requireNotNull(dto.toDomain(Instant.EPOCH))
        assertTrue(result.coastalFloodingAlert != null)
    }
}
