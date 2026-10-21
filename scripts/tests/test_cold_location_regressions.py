"""Static guardrails for cold-start location fixes, widget lifecycle, and privacy."""
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'app/src/main/java/com/meteocompare/app'


def read(path):
    return (BASE / path).read_text()


def test_cold_start_cache_uses_monotonic_age_and_fused_first():
    code = read('data/repository/CurrentLocationController.kt')
    policy = read('domain/util/LocationFixPolicy.kt')
    assert 'SystemClock.elapsedRealtimeNanos()' in code
    assert 'manager.getLastKnownLocation(provider)' in code
    assert 'LocationFixPolicy.isRecentCachedFix' in code
    assert 'LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER' in code
    assert 'CACHED_FIX_MAX_AGE_MS = 3 * 60_000L' in policy
    assert 'EXPLICIT_TIMEOUT_MS = 30_000L' in policy


def test_failed_one_shot_uses_bounded_listener_and_unregisters_on_cancel():
    code = read('data/repository/CurrentLocationController.kt')
    assert 'withTimeoutOrNull(5_000L) { firstUpdate(manager, provider) }' in code
    assert 'manager.requestLocationUpdates(provider, 0L, 0f, listener' in code
    assert 'continuation.invokeOnCancellation' in code
    assert 'manager.removeUpdates(listener)' in code
    assert 'if (explicit)' in code
    assert 'LocationManager.GPS_PROVIDER' not in code.replace('// GPS_PROVIDER', '')


def test_widget_and_home_share_foreground_controller_and_guards():
    ctrl = read('data/repository/CurrentLocationController.kt')
    activity = read('widget/WidgetLocationRefreshActivity.kt')
    vm = read('ui/citylist/CurrentLocationViewModel.kt')
    screen = read('ui/citylist/CityListScreen.kt')
    assert 'controller.refreshExisting(updateWidgetsOnSameCell = true)' in activity
    assert 'controller.enable()' in vm
    assert 'if (locateJob?.isActive == true) return' in vm
    assert 'current_location_progress' in screen
    assert 'lastAutomaticAttemptMs' in ctrl
    assert 'lastExplicitAttemptMs' in ctrl
    assert 'LocationFixPolicy.canReuseFreshSuccess' in ctrl
    assert 'WidgetRefreshScheduler.triggerImmediateRefresh(context)' in ctrl
    assert 'locationVm.cancelLocationRequests()' in screen
    assert 'acquisitionJob?.cancel()' in activity
    assert 'permissionCheckJob?.cancel()' in activity
    assert 'Lifecycle.State.STARTED' in activity


def test_disabled_location_has_distinct_localized_message():
    ctrl = read('data/repository/CurrentLocationController.kt')
    assert 'LocationManagerCompat.isLocationEnabled(manager)' in ctrl
    assert 'Result.ServicesDisabled' in ctrl
    for folder in ('values', 'values-en', 'values-de', 'values-es', 'values-it'):
        root = ElementTree.parse(ROOT / 'app/src/main/res' / folder / 'strings.xml')
        assert len(root.findall(".//string[@name='current_location_services_disabled']")) == 1


def test_no_precision_escalation_and_no_background_location_jobs():
    manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
    assert 'ACCESS_COARSE_LOCATION' in manifest
    assert 'ACCESS_FINE_LOCATION' not in manifest
    assert 'ACCESS_BACKGROUND_LOCATION' not in manifest
    code = read('data/repository/CurrentLocationController.kt')
    assert 'store.save(cell, System.currentTimeMillis())' in code
    assert 'CurrentLocationStore.grid(fix.latitude, fix.longitude)' in code
    assert 'location.latitude' not in code.lower()
    for p in (BASE / 'data/worker').glob('*.kt'):
        assert 'getCurrentLocation(' not in p.read_text()
    for p in (BASE / 'widget').glob('*.kt'):
        assert 'requestLocationUpdates(' not in p.read_text()
