package com.meteocompare.app.widget

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import java.util.concurrent.TimeUnit

/**
 * Tests des CONTRATS de [WidgetRefreshScheduler].
 *
 * ─── Qu'est-ce qu'on protège ici ? ──────────────────────────────────────
 * Chaque test verrouille un invariant issu du fix "widget updates".
 * Un futur refactor qui casserait un de ces invariants ferait échouer un
 * test, avec un message assez précis pour pointer vers le bug de régression.
 *
 * Invariants critiques :
 *   1. Cadence du tick FIXE à 15 min. Fondement du fix "les heures ne
 *      changent pas au fur et à mesure du temps" : le RefreshInterval
 *      utilisateur ne doit PLUS piloter la cadence du worker.
 *   2. Politique KEEP (pas UPDATE). `schedule` doit être idempotent pour
 *      ne pas recréer le job à chaque changement de settings.
 *   3. `triggerImmediateRefresh` enqueue un ONE-TIME, pas un PERIODIC.
 *      Sinon chaque toggle utilisateur créerait un job permanent parallèle
 *      → duplication de requêtes qu'on voulait justement éviter.
 *   4. `cancel` et `schedule` ciblent le MÊME nom unique.
 *   5. Aucune contrainte réseau : le tick tourne offline.
 *
 * ─── Approche technique ────────────────────────────────────────────────
 * On utilise les overloads `internal fun schedule(workManager: WorkManager)`
 * (et `triggerImmediateRefresh`, `cancel`) qui prennent le WorkManager en
 * paramètre — écrites spécifiquement pour la testabilité. Le call-site
 * production wrappe avec `WorkManager.getInstance(context)`, mais les
 * tests appellent directement l'overload avec un mock.
 *
 * Cette architecture évite MockK.mockkStatic sur les APIs Android, qui
 * peut être flaky selon la version de mockk et les stubs Android jar.
 */
class WidgetRefreshSchedulerTest {

    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        workManager = mockk(relaxed = true)
    }

    // ─────────────────────── schedule() ──────────────────────────────────

    @Test
    fun `schedule - cadence FIXE de 15 minutes, indépendante de tout paramètre externe`() {
        // Cœur du fix "heures gelées" : la cadence du tick d'affichage n'a
        // PLUS aucun lien avec la RefreshInterval utilisateur. La signature
        // de `schedule` ne prend même plus d'intervalle — ce test verrouille
        // cette invariance.
        val requestSlot = slot<PeriodicWorkRequest>()
        every {
            workManager.enqueueUniquePeriodicWork(any(), any(), capture(requestSlot))
        } returns mockk(relaxed = true)

        WidgetRefreshScheduler.schedule(workManager)

        val intervalMs = requestSlot.captured.workSpec.intervalDuration
        assertEquals(
            "Cadence du tick doit être exactement 15 minutes en ms",
            TimeUnit.MINUTES.toMillis(15),
            intervalMs
        )
    }

    @Test
    fun `schedule - policy KEEP pour être idempotent`() {
        // Si on utilisait UPDATE, chaque appel de `schedule` recrée le job
        // — au pire, ça remet à zéro le compteur de l'intervalle et le
        // widget peut manquer un tick. KEEP garantit qu'un `schedule` sur
        // un job déjà existant est un no-op.
        val policySlot = slot<ExistingPeriodicWorkPolicy>()
        every {
            workManager.enqueueUniquePeriodicWork(any(), capture(policySlot), any())
        } returns mockk(relaxed = true)

        WidgetRefreshScheduler.schedule(workManager)

        assertEquals(ExistingPeriodicWorkPolicy.KEEP, policySlot.captured)
    }

    @Test
    fun `schedule - utilise le nom unique exposé par WidgetRefreshScheduler`() {
        // Le nom unique DOIT être stable et cohérent avec celui exposé via
        // TESTABLE_WORK_NAME. Cette exposition sert de contrat au test
        // `cancel utilise le même nom que schedule` plus bas.
        val nameSlot = slot<String>()
        every {
            workManager.enqueueUniquePeriodicWork(capture(nameSlot), any(), any())
        } returns mockk(relaxed = true)

        WidgetRefreshScheduler.schedule(workManager)

        assertEquals(WidgetRefreshScheduler.TESTABLE_WORK_NAME, nameSlot.captured)
    }

    @Test
    fun `schedule - PAS de contrainte réseau (tick doit tourner offline pour shifter les heures)`() {
        // Le tick ne doit PAS attendre une connexion réseau pour s'exécuter.
        // Raison : même offline, on veut que les labels d'heure du widget
        // shiftent (14h → 15h au passage d'heure). Le fetch réseau est
        // court-circuité en amont par NetworkMonitor.isOnline() dans le
        // repo, pas ici. Si on avait NETWORK CONNECTED en contrainte, le
        // widget d'un user en avion resterait avec les heures gelées.
        val requestSlot = slot<PeriodicWorkRequest>()
        every {
            workManager.enqueueUniquePeriodicWork(any(), any(), capture(requestSlot))
        } returns mockk(relaxed = true)

        WidgetRefreshScheduler.schedule(workManager)

        val networkType = requestSlot.captured.workSpec.constraints.requiredNetworkType
        assertEquals(
            "Aucune contrainte NETWORK CONNECTED — le tick doit tourner offline",
            NetworkType.NOT_REQUIRED,
            networkType
        )
    }

    // ─────────────────────── triggerImmediateRefresh() ───────────────────

    @Test
    fun `triggerImmediateRefresh - enqueue un ONE-TIME work, pas un PERIODIC`() {
        // Cet appel doit être UN coup — un tick immédiat, pas la création
        // d'un nouveau job périodique parallèle. Si on l'implémentait avec
        // enqueueUniquePeriodicWork on aurait à terme des dizaines de jobs
        // parallèles (un par toggle de settings), source de la duplication
        // de requêtes qu'on voulait justement éliminer.
        WidgetRefreshScheduler.triggerImmediateRefresh(workManager)

        verify(exactly = 1) { workManager.enqueue(any<OneTimeWorkRequest>()) }
        // On ne doit surtout PAS avoir touché à l'enqueue périodique.
        verify(exactly = 0) {
            workManager.enqueueUniquePeriodicWork(any(), any(), any())
        }
    }

    // ─────────────────────── cancel() ────────────────────────────────────

    @Test
    fun `cancel - cible le nom unique du worker, pas tout WorkManager`() {
        // Si on faisait `workManager.cancelAllWork()`, on cancellerait aussi
        // les workers des OTHER features (par exemple un futur job de sync
        // des favoris). Le contrat de `cancel` est ciblé.
        val nameSlot = slot<String>()
        every { workManager.cancelUniqueWork(capture(nameSlot)) } returns mockk(relaxed = true)

        WidgetRefreshScheduler.cancel(workManager)

        verify(exactly = 1) { workManager.cancelUniqueWork(any()) }
        assertEquals(WidgetRefreshScheduler.TESTABLE_WORK_NAME, nameSlot.captured)
    }

    @Test
    fun `cancel - utilise le MEME nom que schedule (sinon ne trouve rien à annuler)`() {
        // Régression classique : `schedule` utilise un nom, `cancel` en
        // utilise un autre → cancel appelle sur un nom inexistant et le
        // job continue à tourner indéfiniment. Ce test relie les deux
        // symboliquement.
        val scheduleName = slot<String>()
        val cancelName = slot<String>()
        every {
            workManager.enqueueUniquePeriodicWork(capture(scheduleName), any(), any())
        } returns mockk(relaxed = true)
        every {
            workManager.cancelUniqueWork(capture(cancelName))
        } returns mockk(relaxed = true)

        WidgetRefreshScheduler.schedule(workManager)
        WidgetRefreshScheduler.cancel(workManager)

        assertEquals(
            "schedule() et cancel() doivent utiliser le même nom unique",
            scheduleName.captured,
            cancelName.captured
        )
    }
}
