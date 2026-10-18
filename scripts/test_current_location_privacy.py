"""Static privacy regressions complement JVM/instrumented tests (no Android SDK required)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/meteocompare/app"


def content(path):
    return (ROOT / path).read_text()


def test_manifest_requires_only_approximate_location():
    manifest = content("app/src/main/AndroidManifest.xml")
    assert 'android.permission.ACCESS_COARSE_LOCATION' in manifest
    assert 'android.permission.ACCESS_FINE_LOCATION' not in manifest
    assert 'android.permission.ACCESS_BACKGROUND_LOCATION' not in manifest
    assert 'android.permission.FOREGROUND_SERVICE_LOCATION' not in manifest


def test_widget_does_not_query_location_device():
    widget = JAVA / "widget"
    for file in widget.glob("*.kt"):
        source = file.read_text()
        assert "getCurrentLocation(" not in source, file.name
        assert "requestLocationUpdates(" not in source, file.name
    assert "WIDGET_CITY_ID" in (widget / "WidgetConfiguration.kt").read_text()
    assert "WIDGET_CITY_ID" in (widget / "WidgetData.kt").read_text()


def test_no_position_tracking_from_background_workers():
    for folder in (JAVA / "data/worker", JAVA / "notification"):
        for file in folder.glob("*.kt"):
            assert "getCurrentLocation(" not in file.read_text(), file.name
    home = (JAVA / "data/repository/CurrentLocationController.kt").read_text()
    assert "LocationManagerCompat.getCurrentLocation(" in home
    assert "hasPermission()" in home
    assert "mutex.withLock" in home
    assert "refreshExisting" in home


def test_gps_store_is_not_backed_up():
    for filename in ("backup_rules.xml", "data_extraction_rules.xml"):
        assert 'datastore/current_location.preferences_pb' in content(f"app/src/main/res/xml/{filename}")
    store = (JAVA / "data/repository/CurrentLocationStore.kt").read_text()
    assert 'preferencesDataStore(name = "current_location")' in store
    assert "fun clear()" in store
    assert "LocationPrivacyGrid.cell(latitude, longitude)" in store
    assert "lastLocation" not in store


def test_no_forecast_or_bias_history_for_mobile_source():
    assert "CurrentLocationStore.isCurrentCity" in (JAVA / "data/repository/ForecastEvolutionRecorder.kt").read_text()
    assert "filterNot { CurrentLocationStore.isCurrentCity(it.id) }" in (JAVA / "data/worker/BiasRefreshWorker.kt").read_text()
    assert "if (!movingZone) runSuspendCatching" in (JAVA / "data/repository/ClimateNormalsRepositoryImpl.kt").read_text()
    assert "if (!currentZone) prefs.edit" in (JAVA / "data/repository/UvIndexRepositoryImpl.kt").read_text()


def test_strings_localized_in_five_languages():
    for folder in ("values", "values-en", "values-es", "values-it", "values-de"):
        strings = content(f"app/src/main/res/{folder}/strings.xml")
        for key in ("current_location_title", "current_location_add", "current_location_privacy_hint", "current_location_stale", "current_location_widget_note"):
            assert f'name="{key}"' in strings, (folder, key)


def test_backed_up_ui_preferences_never_encode_moving_grid_id():
    prefs = (JAVA / "data/preferences/UserPreferencesRepositoryImpl.kt").read_text()
    home = (JAVA / "ui/citylist/CityListViewModel.kt").read_text()
    assert "CurrentLocationStore.WIDGET_CITY_ID else cityId" in prefs
    assert "CurrentLocationStore.WIDGET_CITY_ID" in home
    assert "cityPreferencePrefix(cityId: String)" in prefs
    assert "CurrentLocationStore::isCurrentCity" in prefs
