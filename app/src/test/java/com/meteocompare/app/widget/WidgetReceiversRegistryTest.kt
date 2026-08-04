package com.meteocompare.app.widget

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Tests d'invariance sur [WidgetReceivers.All].
 *
 * ─── Pourquoi c'est important ────────────────────────────────────────
 * Le registry `WidgetReceivers.All` est consulté à deux endroits critiques :
 *
 *   1. [MeteoWidgetReceiver.isAnyReceiverAlive] pour décider si on peut
 *      cancel le worker sur onDisabled. Une classe manquante → un widget
 *      "invisible" pour l'algo → cancel prématuré → widget survivant sans
 *      tick.
 *
 *   2. [WidgetRefreshWorker.doWork] pour cross-checker les glanceIds
 *      contre les vrais widgets vivants. Une classe manquante → tous les
 *      widgets de cette classe traités comme ghosts → aucun tick.
 *
 * Ces deux régressions sont silencieuses en test manuel : l'utilisateur
 * ne voit rien de cassé jusqu'à ce que son widget X ne se mette plus à
 * jour. D'où l'importance d'un test-check.
 *
 * ─── Approche ────────────────────────────────────────────────────────
 * On ne peut pas parser le AndroidManifest.xml en pur JVM test sans dép
 * lourde (pull xmlpull ou lire un asset). À la place, on énumère les
 * classes ATTENDUES dans le registry — un contributeur qui ajoute une
 * variante DOIT :
 *   1. Créer la classe MeteoWidgetReceiverXxx
 *   2. L'ajouter au manifest
 *   3. L'ajouter au registry ET à ce test
 *
 * Les 3 étapes se voient dans la même PR — la review attrape les oublis.
 */
class WidgetReceiversRegistryTest {

    /**
     * Contient exactement les receivers déclarés dans AndroidManifest.xml.
     * Doit être maintenu en sync manuellement — voir docblock ci-dessus
     * pour la justification.
     */
    private val expectedReceivers = setOf<Class<out MeteoWidgetReceiver>>(
        MeteoWidgetReceiver1x1::class.java,
        MeteoWidgetReceiver2x1::class.java,
        MeteoWidgetReceiver3x1::class.java,
        MeteoWidgetReceiver4x1::class.java,
        MeteoWidgetReceiver5x1::class.java,
        MeteoWidgetReceiver2x2::class.java,
        MeteoWidgetReceiver3x2::class.java,
        MeteoWidgetReceiver4x2::class.java,
        MeteoWidgetReceiver5x2::class.java,
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
}
