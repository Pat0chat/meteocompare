"""Release source completeness, visual layout and distribution contracts.

Source assertions do not replace Gradle compilation or on-device UI verification.
"""
from pathlib import Path
import re
import tomllib
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'app/src/main/java/com/meteocompare/app/ui'


class ReleaseSourceCompletenessTest(unittest.TestCase):
    def test_version_and_localized_metadata(self):
        build = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
        version = re.search(r'versionName\s*=\s*"(\d+\.\d+\.\d+)"', build)
        code = re.search(r'versionCode\s*=\s*(\d+)', build)
        self.assertIsNotNone(version)
        self.assertIsNotNone(code)
        name = version.group(1)
        number = code.group(1)
        notes = (ROOT / '.github/release-notes' / f'v{name}.md').read_text(encoding='utf-8')
        self.assertIn('## Français', notes)
        self.assertIn('## English', notes)
        self.assertIn(f'`versionCode`: **{number}**', notes)
        for locale in ('fr-FR', 'en-US', 'es-ES', 'de-DE', 'it-IT', 'tr-TR'):
            with self.subTest(locale=locale):
                note = ROOT / f'fastlane/metadata/android/{locale}/changelogs/{number}.txt'
                text = note.read_text(encoding='utf-8').strip()
                self.assertGreater(len(text), 50)
                self.assertLessEqual(len(text), 500)

    def test_gradle_wrapper_and_catalog_are_packaged_as_sources(self):
        gradle = ROOT / 'gradle'
        props = (gradle / 'wrapper/gradle-wrapper.properties').read_text(encoding='utf-8')
        self.assertIn('gradle-9.7.1-bin.zip', props)
        jar = gradle / 'wrapper/gradle-wrapper.jar'
        self.assertGreater(jar.stat().st_size, 40000)
        with zipfile.ZipFile(jar) as archive:
            self.assertIn('org/gradle/wrapper/GradleWrapperMain.class', archive.namelist())
        catalog = tomllib.loads((gradle / 'libs.versions.toml').read_text(encoding='utf-8'))
        self.assertIn('agp', catalog['versions'])
        self.assertIn('android-application', catalog['plugins'])
        ignored = (ROOT / '.gitignore').read_text(encoding='utf-8').splitlines()
        self.assertNotIn('gradle/', ignored)
        self.assertNotIn('/gradle/', ignored)

    def test_legend_has_single_background_and_stable_symbol_columns(self):
        help_ui = (UI / 'help/HowItWorksScreen.kt').read_text(encoding='utf-8')
        self.assertIn('private fun HelpNavigationMenu(', help_ui)
        self.assertIn('sections.chunked(2)', help_ui)
        self.assertIn('MaterialTheme.colorScheme.surfaceContainerLow', help_ui)
        self.assertIn('testTag("help_menu_${section.step}")', help_ui)
        self.assertNotIn('AssistChip(', help_ui)

    def test_deleted_status_legend_has_no_kotlin_callers_including_debug_previews(self):
        # Debug @Preview sources compile with the app: deleted UI components must
        # not survive there even if the release UI contains no such references.
        for source_set in ('main', 'debug', 'androidTest', 'test'):
            source_root = ROOT / 'app/src' / source_set
            for source in source_root.rglob('*.kt'):
                with self.subTest(path=str(source.relative_to(ROOT))):
                    self.assertNotIn('ConditionStatusLegend', source.read_text(encoding='utf-8'))

    def test_contextual_placement_and_home_remain_unchanged(self):
        for relative in (
            'citylist/CityListScreen.kt', 'citydetail/SimplifiedTimeline.kt',
            'citydetail/WeatherByModelTable.kt', 'graphicview/GraphicForecastScreen.kt',
            'enginecomparison/EngineComparisonScreen.kt'):
            self.assertNotIn('ConditionStatusLegend', (UI / relative).read_text(encoding='utf-8'))
        self.assertFalse((UI / 'components/ConditionStatusLegend.kt').exists())

if __name__ == '__main__':
    unittest.main()
