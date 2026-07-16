package com.meteocompare.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Test runner custom qui remplace [MeteoCompareApplication] par [HiltTestApplication]
 * pour les tests d'instrumentation.
 *
 * Pourquoi : la vraie [MeteoCompareApplication] est annotée @HiltAndroidApp et
 * initialise toute la chaîne d'injection avec les vrais bindings. Les tests
 * remplacent le module repository par des fakes déterministes via
 * `@TestInstallIn`, afin que navigation et UI ne dépendent ni du réseau, ni
 * d'une base persistante, ni de l'état laissé par un test précédent.
 *
 * Référencé par `testInstrumentationRunner` dans `app/build.gradle.kts`.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        context: Context?
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
