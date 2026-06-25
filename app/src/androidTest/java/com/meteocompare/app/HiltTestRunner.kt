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
 * initialise toute la chaîne d'injection avec les vrais bindings (Room, Retrofit,
 * OkHttp, DataStore réel). Pour les tests on veut pouvoir remplacer certains
 * modules — par exemple OkHttp avec un MockWebServer — et cela n'est possible
 * qu'avec [HiltTestApplication] qui supporte `@UninstallModules`.
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
