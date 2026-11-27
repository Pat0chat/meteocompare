package com.meteocompare.app.domain.model

import java.time.Duration
import java.time.Instant

/** Shared alert threshold; independent of the automatic refresh interval. */
internal val STALE_FORECAST_AGE: Duration = Duration.ofHours(24)

internal fun isForecastStale(fetchedAt: Instant?, now: Instant): Boolean =
    fetchedAt != null && Duration.between(fetchedAt, now) >= STALE_FORECAST_AGE
