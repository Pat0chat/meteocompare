package com.meteocompare.app.domain.model

/**
 * A thin/high-cloud *appearance*, not a change to the reported cloud-cover fraction.
 * The high-cloud profile alone does not prove transparency: require sunshine/solar
 * evidence from the SAME model. Solar fields describe the preceding hour, so
 * callers must align them with the cloud-cover hour before using this function.
 */
object SolarSkyInterpretation {
    fun isVeiledSky(
        totalCloudPercent: Int?,
        lowCloudPercent: Int?,
        midCloudPercent: Int?,
        highCloudPercent: Int?,
        sunshineSeconds: Double?,
        directNormalIrradianceWm2: Double?
    ): Boolean {
        if (totalCloudPercent == null || totalCloudPercent !in 90..100) return false
        val low = lowCloudPercent?.takeIf { it in 0..100 } ?: return false
        val mid = midCloudPercent?.takeIf { it in 0..100 } ?: return false
        val high = highCloudPercent?.takeIf { it in 0..100 } ?: return false
        if (low > 25 || mid > 35 || high < 80) return false
        val duration = sunshineSeconds?.takeIf { it.isFinite() && it in 0.0..3600.0 }
        val irradiance = directNormalIrradianceWm2?.takeIf { it.isFinite() && it in 0.0..1500.0 }
        // Explicit sunshine duration is more authoritative than the DNI mean.
        return if (duration != null) duration >= 900.0 else irradiance != null && irradiance >= 160.0
    }

    /** WMO precedence: don't hide fog, rain, snow or thunderstorms behind 'veiled'. */
    fun refine(condition: WeatherCondition?, veiled: Boolean): WeatherCondition? =
        if (condition == WeatherCondition.OVERCAST && veiled) WeatherCondition.VEILED else condition
}
