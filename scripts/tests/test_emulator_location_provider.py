"""Regression contracts for optional precise emulator fix and privacy invariants."""
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java/com/meteocompare/app"


def content(rel):
    return (ROOT / rel).read_text()


def test_precise_gps_is_conditional_and_approximate_fallback_remains():
    controller = (SRC / 'data/repository/CurrentLocationController.kt').read_text()
    policy = (SRC / 'domain/util/LocationProviderPolicy.kt').read_text()
    assert 'if (fineGranted) add(Source.GPS)' in policy
    assert 'if (apiLevel >= 31) add(Source.FUSED)' in policy
    assert 'add(Source.NETWORK)' in policy
    assert 'LocationProviderPolicy.priority(Build.VERSION.SDK_INT, hasFinePermission())' in controller
    assert 'LocationManager.GPS_PROVIDER' in controller
    assert 'manager.getLastKnownLocation(provider)' in controller
    assert 'Result.ApproximateUnavailable' in controller


def test_android_permission_request_is_batched_and_only_from_foreground_activities():
    code = (SRC / 'data/repository/LocationPermissionOffer.kt').read_text()
    home = (SRC / 'ui/citylist/CityListScreen.kt').read_text()
    widget = (SRC / 'widget/WidgetLocationRefreshActivity.kt').read_text()
    assert 'Manifest.permission.ACCESS_FINE_LOCATION' in code
    assert 'Manifest.permission.ACCESS_COARSE_LOCATION' in code
    assert 'PRECISION_OFFERED' in code
    assert 'LocationPermissionPolicy.shouldRequest' in code
    assert 'RequestMultiplePermissions()' in home
    assert 'RequestMultiplePermissions()' in widget
    assert 'markPrecisionOffered(context)' in home
    assert 'markPrecisionOffered(this, explicitWidgetAction = true)' in widget
    assert 'shouldRequest(context)' in home
    assert 'shouldRequest(this, explicitWidgetAction = true)' in widget
    assert 'LocationPermissionOffer.hasAny' in home
    assert 'LocationPermissionOffer.hasAny' in widget


def test_raw_location_remains_ephemeral_and_no_background_location():
    controller = (SRC / 'data/repository/CurrentLocationController.kt').read_text()
    store = (SRC / 'data/repository/CurrentLocationStore.kt').read_text()
    manifest = content('app/src/main/AndroidManifest.xml')
    assert 'android.permission.ACCESS_FINE_LOCATION' in manifest
    assert 'android.permission.ACCESS_COARSE_LOCATION' in manifest
    assert 'ACCESS_BACKGROUND_LOCATION' not in manifest
    assert 'CurrentLocationStore.grid(fix.latitude, fix.longitude)' in controller
    assert 'store.save(cell, System.currentTimeMillis())' in controller
    assert 'prefs[latKey] = cell.first' in store
    assert 'prefs[lonKey] = cell.second' in store
    assert 'continuation.invokeOnCancellation' in controller
    assert 'manager.removeUpdates(listener)' in controller
    assert '"Foreground providers="' in controller
    assert '"latitude="' not in controller
    for p in (SRC / 'widget').glob('*.kt'):
        assert 'requestLocationUpdates(' not in p.read_text()
    for p in (SRC / 'data/worker').glob('*.kt'):
        assert 'getCurrentLocation(' not in p.read_text()


def test_privacy_explanation_and_actionable_failures_in_five_locales():
    for loc in ('values', 'values-en', 'values-de', 'values-es', 'values-it'):
        parsed = ElementTree.parse(ROOT / 'app/src/main/res' / loc / 'strings.xml')
        for key in ('current_location_privacy_hint', 'current_location_approximate_unavailable'):
            assert len(parsed.findall(f".//string[@name='{key}']")) == 1
