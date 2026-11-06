"""Protect the Android version / GitHub notes / 5 Play Store changelog contract."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / "app/build.gradle.kts"
LOCALES = ("fr-FR", "en-US", "es-ES", "de-DE", "it-IT")


def android_version():
    source = BUILD.read_text(encoding="utf-8")
    name = re.search(r'(?m)^\s*versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', source)
    code = re.search(r'(?m)^\s*versionCode\s*=\s*([0-9]+)', source)
    if not name or not code:
        raise AssertionError("Unable to determine the Android release version")
    return name.group(1), int(code.group(1))


class ReleaseMetadataContract(unittest.TestCase):
    def test_version_has_matching_github_release_notes(self):
        name, code = android_version()
        notes = ROOT / ".github/release-notes" / f"v{name}.md"
        self.assertTrue(notes.is_file(), str(notes))
        text = notes.read_text(encoding="utf-8")
        self.assertIn(f"MeteoCompare {name}", text)
        self.assertIn(f"`versionCode`: **{code}**", text)
        self.assertIn("## Français", text)
        self.assertIn("## English", text)

    def test_each_supported_play_locale_has_changelog_for_current_build(self):
        _, code = android_version()
        for locale in LOCALES:
            with self.subTest(locale=locale):
                path = ROOT / "fastlane/metadata/android" / locale / "changelogs" / f"{code}.txt"
                self.assertTrue(path.is_file(), str(path))
                content = path.read_text(encoding="utf-8").strip()
                self.assertGreater(len(content), 50)
                self.assertLessEqual(len(content), 500)


if __name__ == "__main__":
    unittest.main()
