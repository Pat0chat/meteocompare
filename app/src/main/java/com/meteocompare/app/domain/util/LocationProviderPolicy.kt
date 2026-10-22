package com.meteocompare.app.domain.util

/** Foreground-only provider order. GPS is never consulted without explicit fine consent. */
object LocationProviderPolicy {
    enum class Source { GPS, FUSED, NETWORK }

    fun priority(apiLevel: Int, fineGranted: Boolean): List<Source> = buildList {
        if (fineGranted) add(Source.GPS)
        if (apiLevel >= 31) add(Source.FUSED)
        add(Source.NETWORK)
    }
}
