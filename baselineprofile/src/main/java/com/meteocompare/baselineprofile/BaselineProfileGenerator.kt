package com.meteocompare.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    @Test
    fun homeScrolling() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = false
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Parcours robuste même lorsque la Home est vide : si une liste de
        // villes est présente, ces gestes compilent les chemins de scroll et
        // les CityCards ; sinon ils restent de simples no-op côté contenu.
        repeat(3) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.78f).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.30f).toInt(),
                12
            )
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.meteocompare.app"
    }
}
