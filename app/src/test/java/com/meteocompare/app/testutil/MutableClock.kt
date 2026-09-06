package com.meteocompare.app.testutil

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Horloge pilotable pour vérifier les changements temporels sans attendre. */
internal class MutableClock(
    var currentInstant: Instant,
    private val currentZone: ZoneId = ZoneOffset.UTC
) : Clock() {
    override fun getZone(): ZoneId = currentZone

    override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant
}
