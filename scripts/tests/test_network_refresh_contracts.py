"""Static wiring checks complement the JVM and instrumented concurrency tests.

These do not prove runtime behavior. They catch accidental reinstatement of the
widget double-dispatch and accidental removal of the per-key request registry.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/src/main/java/com/meteocompare/app'


def kotlin(relative):
    return (SOURCE / relative).read_text(encoding='utf-8')


class NetworkRefreshContractsTest(unittest.TestCase):
    def test_widget_configuration_has_one_direct_render_and_no_broadcast(self):
        code = kotlin('widget/MeteoWidgetConfigActivity.kt')
        method = code.split('private fun persistAndFinish(', 1)[1].split('    companion object {', 1)[0]
        self.assertEqual(1, method.count('.update(appCtx, glanceId)'))
        self.assertNotIn('sendBroadcast(', method)
        self.assertIn('WidgetPreferences.LastDispatchAtKey', method)
        self.assertIn('if (saveInProgress) return', method)

    def test_launcher_callback_uses_cadence_guard_not_forced_worker(self):
        code = kotlin('widget/MeteoWidgetReceiver.kt')
        update = code.split('override fun onUpdate(', 1)[1].split('override fun onRestored(', 1)[0]
        self.assertIn('triggerCatchUpRefresh(context)', update)
        self.assertNotIn('triggerImmediateRefresh(context)', update)
        self.assertIn('super.onUpdate(', update)

    def test_system_clock_changes_remain_forced(self):
        code = kotlin('widget/WidgetRefreshScheduler.kt')
        method = code.split('fun triggerSystemRefresh(workManager: WorkManager)', 1)[1].split('fun triggerCatchUpRefresh(', 1)[0]
        self.assertIn('force = true', method)
        self.assertIn('WidgetRefreshReason.CATCH_UP', method)
        self.assertIn('triggerSystemRefresh(appContext)', kotlin('widget/WidgetRefreshRepairReceiver.kt'))

    def test_vigilance_shared_fetch_not_cancelled_by_first_screen(self):
        code = kotlin('data/repository/VigilanceRepositoryImpl.kt')
        shared = kotlin('data/repository/SharedVigilanceRequests.kt')
        self.assertIn('sharedRequests.getOrStart(requestKey)', code)
        self.assertNotIn('requestLocks.getOrPut', code)
        self.assertIn('scope.async(start = CoroutineStart.LAZY)', shared)
        self.assertIn('return deferred.await()', shared)

    def test_forecast_counters_are_wired_to_events(self):
        code = kotlin('data/repository/ForecastRepositoryImpl.kt')
        for event in ('cacheRead(', 'fetchStarted()', 'fetchCoalesced()', 'logFetchDiagnostics()'):
            self.assertIn(event, code)
        self.assertIn('val batched = api.getForecastBatched(', code)
        self.assertLess(code.index('fetchDiagnostics.fetchStarted()'), code.index('val batched = api.getForecastBatched('))


if __name__ == '__main__':
    unittest.main()
