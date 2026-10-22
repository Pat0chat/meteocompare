"""Security, concurrency and release contracts for v1.19.1."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'app/src/main/java/com/meteocompare/app'

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def test_manual_fix_has_stricter_platform_cache_age():
    policy = read('app/src/main/java/com/meteocompare/app/domain/util/LocationFixPolicy.kt')
    controller = read('app/src/main/java/com/meteocompare/app/data/repository/CurrentLocationController.kt')
    assert 'EXPLICIT_CACHED_FIX_MAX_AGE_MS = 15_000L' in policy
    assert 'CACHED_FIX_MAX_AGE_MS = 3 * 60_000L' in policy
    assert 'explicit = explicit' in controller

def test_opt_out_preempts_running_fix_and_blocks_late_save():
    c = read('app/src/main/java/com/meteocompare/app/data/repository/CurrentLocationController.kt')
    assert 'disableGeneration.incrementAndGet()' in c
    assert 'activeFix.get()?.cancel()' in c
    assert c.index('disableGeneration.incrementAndGet()') < c.index('mutex.withLock {', c.index('suspend fun disable()'))
    assert 'LocationFixPolicy.mayCommitFix(' in c
    assert c.index('LocationFixPolicy.mayCommitFix(', c.index('val cell =')) < c.index('store.save(cell, System.currentTimeMillis())')
    assert c.index('store.save(cell, System.currentTimeMillis())') < c.index('if (disableGeneration.get() != generation) return@withLock Result.Unchanged', c.index('store.save(cell, System.currentTimeMillis())'))

def test_manual_preempts_auto_and_cancellation_is_not_swallowed():
    c = read('app/src/main/java/com/meteocompare/app/data/repository/CurrentLocationController.kt')
    assert 'if (force) automaticFix.get()?.cancel()' in c
    assert 'manualGeneration.incrementAndGet()' in c
    assert 'manualGeneration.get() != manualAtStart' in c
    assert 'currentCoroutineContext().isActive' in c
    assert 'runSuspendCatching { forecasts.clearCacheForCity(oldId) }' in c
    assert 'continuation.invokeOnCancellation { cancellation.cancel() }' in c
    assert 'manager.removeUpdates(listener)' in c

def test_no_background_gps_and_store_is_grid_only():
    manifest = read('app/src/main/AndroidManifest.xml')
    assert 'ACCESS_BACKGROUND_LOCATION' not in manifest
    controller = read('app/src/main/java/com/meteocompare/app/data/repository/CurrentLocationController.kt')
    store = read('app/src/main/java/com/meteocompare/app/data/repository/CurrentLocationStore.kt')
    assert 'CurrentLocationStore.grid(fix.latitude, fix.longitude)' in controller
    assert 'prefs[latKey] = cell.first' in store and 'prefs[lonKey] = cell.second' in store
    for folder in ('widget','data/worker'):
        for source in (SRC / folder).glob('*.kt'):
            assert 'requestLocationUpdates(' not in source.read_text(encoding="utf-8")


def test_backup_excludes_current_location_and_consent_history():
    files = [ROOT / 'app/src/main/res/xml/backup_rules.xml', ROOT / 'app/src/main/res/xml/data_extraction_rules.xml']
    for f in files:
        contents = f.read_text(encoding='utf-8')
        assert '<include' in contents
        assert '<include domain="file" path="datastore/current_location.preferences_pb"' not in contents
        assert 'current_location_permission_ui' not in contents
        ET.parse(f)

def test_version_and_localized_release_notes():
    build = read('app/build.gradle.kts')
    assert re.search(r'\bversionCode = 47\b', build)
    assert 'versionName = "1.19.1"' in build
    assert (ROOT/'.github/release-notes/v1.19.1.md').is_file()
    for locale in ('fr-FR','en-US','de-DE','es-ES','it-IT'):
        note=ROOT/f'fastlane/metadata/android/{locale}/changelogs/47.txt'
        assert note.read_text(encoding='utf-8').strip()

def test_wall_clock_rollback_marks_stored_cell_stale():
    store=read('app/src/main/java/com/meteocompare/app/data/repository/CurrentLocationStore.kt')
    policy=read('app/src/main/java/com/meteocompare/app/domain/util/LocationFixPolicy.kt')
    assert 'LocationFixPolicy.isStoredCellStale' in store
    assert 'savedAtMs > nowMs' in policy
