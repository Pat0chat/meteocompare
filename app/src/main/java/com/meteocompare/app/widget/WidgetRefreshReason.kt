package com.meteocompare.app.widget

/** Origine logique d'une mise à jour, sans dépendre des messages WorkManager. */
internal enum class WidgetRefreshReason { PERIODIC, CONFIGURATION, MANUAL, CATCH_UP }

internal fun widgetRefreshReason(raw: String?, isPeriodic: Boolean = false): WidgetRefreshReason =
    WidgetRefreshReason.entries.firstOrNull { it.name == raw }
        ?: if (isPeriodic) WidgetRefreshReason.PERIODIC else WidgetRefreshReason.CATCH_UP
