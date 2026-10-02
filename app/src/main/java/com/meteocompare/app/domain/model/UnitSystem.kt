package com.meteocompare.app.domain.model

/** Display preference only. API payloads, stored values and calculations remain metric. */
enum class UnitSystem(val storageKey: String) {
    METRIC("metric"),
    IMPERIAL("imperial");

    companion object {
        fun fromStorage(value: String?): UnitSystem =
            entries.firstOrNull { it.storageKey == value } ?: METRIC
    }
}
