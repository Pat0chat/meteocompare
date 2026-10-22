"""Guardrails for the user-initiated widget consent path after one-time GPS grants."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'app/src/main/java/com/meteocompare/app'

def code(rel):
    return (SRC / rel).read_text()

def test_one_time_grant_may_expire_while_approximate_grant_survives():
    policy = code('domain/util/LocationPermissionPolicy.kt')
    offer = code('data/repository/LocationPermissionOffer.kt')
    assert 'previouslyFineGranted && !recoveryOffered -> true' in policy
    assert '!coarseGranted -> true' in policy
    assert 'explicitWidgetAction && !widgetReofferUsed -> true' in policy
    assert 'FINE_GRANTED_BEFORE' in offer
    assert 'PRECISION_RECOVERY_OFFERED' in offer
    assert 'WIDGET_REOFFER_USED' in offer
    assert 'recordPermissionResult' in offer
    assert 'putBoolean(PRECISION_RECOVERY_OFFERED, false)' in offer

def test_explicit_widget_request_is_foreground_and_bounded():
    activity = code('widget/WidgetLocationRefreshActivity.kt')
    assert 'private var permissionRequestPending = false' in activity
    assert 'private var permissionRequestedThisAction = false' in activity
    assert 'startAttemptOnStart = true' in activity
    assert 'override fun onStart()' in activity
    assert 'if (startAttemptOnStart)' in activity
    assert 'if (permissionRequestPending || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))' in activity
    assert 'LocationPermissionOffer.shouldRequest(this, explicitWidgetAction = true)' in activity
    assert 'LocationPermissionOffer.recordPermissionResult(this)' in activity
    assert '!permissionRequestedThisAction && !LocationPermissionOffer.hasAny' in activity
    assert 'permissionCheckJob?.cancel()' in activity
    assert 'acquisitionJob?.cancel()' in activity

def test_foreground_acquisition_reports_permission_loss_not_gps_failure():
    controller = code('data/repository/CurrentLocationController.kt')
    assert 'if (!hasPermission())' in controller
    assert 'Foreground location permission expired' in controller
    assert 'return@withLock Result.PermissionMissing' in controller

def test_no_background_location_or_coordinate_persistence_added():
    controller = code('data/repository/CurrentLocationController.kt')
    manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
    assert 'ACCESS_BACKGROUND_LOCATION' not in manifest
    assert 'CurrentLocationStore.grid(fix.latitude, fix.longitude)' in controller
    assert 'store.save(cell, System.currentTimeMillis())' in controller
    assert 'GPS_PROVIDER' in controller
    assert 'hasFinePermission()' in controller
    offer = code('data/repository/LocationPermissionOffer.kt')
    assert 'longitude' not in offer.lower()
    assert 'latitude' not in offer.lower()
