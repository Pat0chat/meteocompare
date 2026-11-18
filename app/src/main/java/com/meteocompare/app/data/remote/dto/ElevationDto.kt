package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ElevationDto(val elevation: List<Double?> = emptyList())
