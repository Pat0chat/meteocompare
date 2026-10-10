package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le registre utilisé par le scheduler doit correspondre exactement aux deux
 * receivers déclarés dans AndroidManifest.xml, sans doublon.
 * Une entrée manquante empêcherait la détection et le rafraîchissement
 * des widgets encore présents sur l'écran d'accueil.
 */
class WidgetReceiversRegistryTest {

    /**
     * Contient exactement les receivers déclarés dans AndroidManifest.xml.
     * Doit être maintenu en sync manuellement — voir docblock ci-dessus
     * pour la justification.
     */
    private val expectedReceivers = setOf<Class<out MeteoWidgetReceiver>>(
        MeteoWeatherWidgetReceiver::class.java,
        MeteoInsightWidgetReceiver::class.java
    )

    @Test
    fun `registry contient exactement les receivers déclarés en manifest`() {
        val actual = WidgetReceivers.All.toSet()

        assertEquals(
            "Nombre de receivers dans WidgetReceivers.All",
            expectedReceivers.size,
            actual.size
        )
        assertEquals(
            "Le registry doit contenir exactement les classes attendues",
            expectedReceivers,
            actual
        )
    }

    @Test
    fun `registry ne contient pas de doublons`() {
        val list = WidgetReceivers.All
        assertEquals(
            "Chaque classe ne doit apparaître qu'une fois dans le registry",
            list.size,
            list.toSet().size
        )
    }

    @Test
    fun `toutes les classes du registry sont des sous-classes de MeteoWidgetReceiver`() {
        // Verrouille l'invariant : `WidgetReceivers.All` ne contient QUE
        // des receivers du widget MeteoCompare. Éviter qu'un jour on y
        // fourre un receiver d'un autre feature (notifications ?) par
        // erreur de refactor.
        WidgetReceivers.All.forEach { clazz ->
            assertTrue(
                "$clazz doit hériter de MeteoWidgetReceiver",
                MeteoWidgetReceiver::class.java.isAssignableFrom(clazz)
            )
        }
    }
    @Test
    fun `provider ownership requires app package and registered receiver`() {
        val registered = MeteoWeatherWidgetReceiver::class.java.name

        assertTrue(
            isOwnedWidgetProvider(
                appPackageName = "com.meteocompare.app",
                providerPackageName = "com.meteocompare.app",
                providerClassName = registered
            )
        )
        assertFalse(
            isOwnedWidgetProvider(
                appPackageName = "com.meteocompare.app",
                providerPackageName = "com.other.app",
                providerClassName = registered
            )
        )
        assertFalse(
            isOwnedWidgetProvider(
                appPackageName = "com.meteocompare.app",
                providerPackageName = "com.meteocompare.app",
                providerClassName = "com.meteocompare.app.widget.UnknownReceiver"
            )
        )
    }

}
