"""Release 1.20+ consistency and publishing gates (no Android SDK required).

These guardrails check the source release package, not runtime behavior.
"""
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCALES = ('fr-FR', 'en-US', 'es-ES', 'de-DE', 'it-IT')


class Release120ReadinessTests(unittest.TestCase):
    def test_release_bump_and_localized_changelogs_are_in_sync(self):
        gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
        name = re.search(r'(?m)^\s*versionName\s*=\s*"(\d+\.\d+\.\d+)"\s*$', gradle)
        code = re.search(r'(?m)^\s*versionCode\s*=\s*(\d+)\s*$', gradle)
        self.assertIsNotNone(name)
        self.assertIsNotNone(code)
        self.assertGreaterEqual(tuple(map(int, name.group(1).split('.'))), (1, 20, 0))
        self.assertGreaterEqual(int(code.group(1)), 52)
        notes = ROOT / '.github/release-notes' / f'v{name.group(1)}.md'
        self.assertTrue(notes.is_file())
        contents = notes.read_text(encoding='utf-8')
        self.assertIn('## Français', contents)
        self.assertIn('## English', contents)
        self.assertIn(f'`versionName`: **{name.group(1)}**', contents)
        self.assertIn(f'`versionCode`: **{code.group(1)}**', contents)
        for loc in LOCALES:
            with self.subTest(locale=loc):
                p = ROOT / f'fastlane/metadata/android/{loc}/changelogs/{code.group(1)}.txt'
                self.assertTrue(p.is_file())
                text = p.read_text(encoding='utf-8').strip()
                self.assertTrue(text)
                self.assertLessEqual(len(text), 500)

    def test_release_ci_blocks_publication_until_emulator_suite_is_green(self):
        pipeline = (ROOT / '.github/workflows/release-fdroid.yml').read_text(encoding='utf-8')
        self.assertRegex(pipeline, r'(?m)^  android-device-gate:\s*$')
        self.assertRegex(pipeline, r'(?m)^  build-apk:\s*\n    needs: \[android-device-gate\]')
        self.assertIn(':app:connectedDebugAndroidTest', pipeline)
        self.assertIn('GITHUB_REF_NAME', pipeline)
        self.assertIn('versionName', pipeline)

    def test_play_store_location_disclosure_agrees_with_manifest(self):
        manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
        play = (ROOT / 'play-store/DATA_SAFETY.md').read_text(encoding='utf-8')
        checklist = (ROOT / 'play-store/README.md').read_text(encoding='utf-8')
        for permission in ('ACCESS_COARSE_LOCATION', 'ACCESS_FINE_LOCATION'):
            self.assertIn(permission, manifest)
            self.assertIn(permission, play)
            self.assertIn(permission, checklist)
        self.assertNotIn('uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"', manifest)
        self.assertIn('Ma position', play)
        self.assertNotIn('Ne pas déclarer une permission de localisation', play)
        self.assertIn('Play Console', play)
        self.assertIn('scripts/verify-16kb-page-size.py', checklist)


if __name__ == '__main__':
    unittest.main()
