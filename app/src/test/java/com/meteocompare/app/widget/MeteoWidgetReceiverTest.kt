package com.meteocompare.app.widget

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Tests du helper [WidgetReceivers.anyAliveWith] — le cœur testable de la
 * fonction qui décide si le worker WorkManager peut être annulé sur
 * `onDisabled`.
 *
 * ─── Ce qu'on protège ici ────────────────────────────────────────────
 * Depuis l'ajout des variantes de taille (11 receivers coexistants), les
 * callbacks système `onDisabled` sont appelés PAR RECEIVER — pas
 * globalement. Cas critique :
 *
 *   - L'user drop UN widget Standard 2×1 et UN widget Large 5×2.
 *   - `onEnabled(Standard)` puis `onEnabled(Large)` sont appelés →
 *     schedule le worker (idempotent grâce à UPDATE).
 *   - L'user retire le Standard. `onDisabled(Standard)` est appelé.
 *   - Le worker DOIT continuer à tourner pour le Large qui reste.
 *
 * Si le check "any alive" retournait false ici, le worker serait cancellé →
 * le widget Large aurait ses labels d'heure gelés, on retomberait dans le
 * bug initial "les heures ne changent pas".
 *
 * ─── Pourquoi ne pas tester `anyAlive(context, awm)` directement ? ──────
 * Cette overload dépend de :
 *   1. `ComponentName(context, clazz)` — dont le constructeur throw
 *      "Stub!" dans la JVM des unit tests (Android SDK non chargé).
 *   2. Le mock de `AppWidgetManager` — Android SDK, marche mal sans
 *      mockk-agent-jvm en dep de test.
 *
 * Le refactor a extrait un helper interne [WidgetReceivers.anyAliveWith]
 * qui prend un lookup callback pur. Toute la logique métier (itération
 * sur `All`) vit dans ce helper — pas de perte de couverture. Le wrapper
 * `anyAlive(context, awm)` ne fait qu'assembler les args pour le lookup
 * réel, testé indirectement en instrumented test si besoin.
 */
class MeteoWidgetReceiverTest {

    /**
     * Construit un lookup pour simuler "ces receivers ont des widgets vivants,
     * les autres non". Utilise un varargs de `Class<...>` — API plus lisible
     * qu'un Set explicite dans les tests, et fait remonter au compilateur
     * les fautes de frappe sur les noms de classe.
     */
    private fun lookupAlive(vararg receiversWithWidgets: Class<out MeteoWidgetReceiver>):
        (Class<out MeteoWidgetReceiver>) -> Boolean {
        val liveSet = receiversWithWidgets.toSet()
        return { clazz -> clazz in liveSet }
    }

    @Test
    fun `anyAliveWith - aucun receiver n'a de widget vivant retourne false`() {
        // Point de sortie : tous les widgets ont été retirés, le worker
        // WorkManager peut être cancellé pour économiser la batterie.
        val alive = WidgetReceivers.anyAliveWith { false }

        assertFalse(alive)
    }

    @Test
    fun `anyAliveWith - Standard vivant retourne true`() {
        val alive = WidgetReceivers.anyAliveWith(
            lookupAlive(MeteoWidgetReceiver2x1::class.java)
        )

        assertTrue(alive)
    }

    @Test
    fun `anyAliveWith - seul un widget Large est vivant, Standard vide - RETOURNE VRAI`() {
        // Régression du bug le plus dangereux : si le check ne scannait que
        // Standard, ce test échouerait. La méthode DOIT scanner tous les
        // receivers, sinon le retrait du dernier Standard tuerait le
        // worker qui sert encore un Large — labels d'heure du Large gelés.
        val alive = WidgetReceivers.anyAliveWith(
            lookupAlive(MeteoWidgetReceiver5x2::class.java)
        )

        assertTrue(alive)
    }

    @Test
    fun `anyAliveWith - un widget Tiny vivant suffit à retourner true`() {
        // Test symétrique avec le Tiny — il fait autant partie du registry
        // que le Standard. Si un contributeur "oubliait" MeteoWidgetReceiver1x1
        // dans WidgetReceivers.All, ce test échouerait parce que la fonction
        // ne verrait pas le widget vivant.
        val alive = WidgetReceivers.anyAliveWith(
            lookupAlive(MeteoWidgetReceiver1x1::class.java)
        )

        assertTrue(alive)
    }

    @Test
    fun `anyAliveWith - scanne LES 11 receivers du registry, aucun ne doit être zappé`() {
        // Test paramétré : pour CHAQUE receiver du registry, on simule
        // "seul ce receiver a un widget" et on vérifie que le check
        // retourne true. Si demain quelqu'un oublie d'inclure une nouvelle
        // variante dans le scan, un des sous-tests échouera avec un
        // message précis (nom de la classe).
        for (clazz in WidgetReceivers.All) {
            val alive = WidgetReceivers.anyAliveWith(lookupAlive(clazz))
            assertTrue(
                "Le scan doit détecter un widget vivant sur $clazz",
                alive
            )
        }
    }

    @Test
    fun `anyAliveWith - court-circuite dès qu'un receiver vivant est trouvé`() {
        // Optimisation subtile : `All.any(...)` short-circuit sur le premier
        // true. Sur un utilisateur avec 11 widgets, on ne veut PAS interroger
        // les 9 launcher-ids pour rien. Ce test vérifie qu'on n'appelle pas
        // le lookup au-delà du premier true.
        var callsAfterFirstAlive = 0
        var seenFirst = false
        WidgetReceivers.anyAliveWith { clazz ->
            if (seenFirst) callsAfterFirstAlive++
            if (clazz == WidgetReceivers.All.first()) {
                seenFirst = true
                true  // premier true — la boucle DOIT s'arrêter ici
            } else {
                false
            }
        }

        assertTrue(
            "any() doit court-circuiter au premier true, pas continuer à interroger",
            callsAfterFirstAlive == 0
        )
    }
    @Test
    fun `liveWidgetIdsWith - fusionne les onze providers et deduplique les ids`() {
        val first = WidgetReceivers.All.first()
        val second = WidgetReceivers.All[1]

        val ids = WidgetReceivers.liveWidgetIdsWith { clazz ->
            when (clazz) {
                first -> listOf(10, 20)
                second -> listOf(20, 30)
                else -> emptyList()
            }
        }

        org.junit.Assert.assertEquals(listOf(10, 20, 30), ids)
    }

    @Test
    fun `liveWidgetIdsWith - aucun provider retourne une liste vide`() {
        val ids = WidgetReceivers.liveWidgetIdsWith { emptyList() }

        org.junit.Assert.assertTrue(ids.isEmpty())
    }

}
