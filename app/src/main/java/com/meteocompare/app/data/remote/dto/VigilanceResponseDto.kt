package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class VigilanceResponseDto(
    val source: String = "Météo-France",
    val configured: Boolean = true,
    val unavailable: Boolean = false,
    val department: String = "",
    val includeCoast: Boolean = false,
    val updateTime: String? = null,
    val productDatetime: String? = null,
    val generationTimestamp: String? = null,
    val periods: List<VigilancePeriodDto> = emptyList()
)

@Serializable
data class VigilancePeriodDto(
    val term: String? = null,
    val beginTime: String? = null,
    val endTime: String? = null,
    val maxColorId: Int = 1,
    val departmentMaxColorId: Int? = null,
    val coastMaxColorId: Int? = null,
    val phenomena: List<VigilancePhenomenonDto> = emptyList()
)

@Serializable
data class VigilancePhenomenonDto(
    val id: String,
    val maxColorId: Int = 1,
    val intervals: List<VigilanceIntervalDto> = emptyList()
)

@Serializable
data class VigilanceIntervalDto(
    val beginTime: String? = null,
    val endTime: String? = null,
    val colorId: Int = 1,
    val scope: String = "department",
    val timingApproximate: Boolean = false
)

@Serializable
data class VigilanceCacheRecord(
    val fetchedAtEpochMs: Long,
    val response: VigilanceResponseDto
)
