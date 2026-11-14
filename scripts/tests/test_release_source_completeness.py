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
        code = (UI / 'components/ConditionStatusLegend.kt').read_text(encoding='utf-8')
        compact = code.split('internal fun ConditionStatusLegend', 1)[1].split(
            'private fun ConditionLegendDialog', 1)[0]
        self.assertEqual(1, compact.count('Surface('))
        self.assertIn('color = MaterialTheme.colorScheme.surfaceContainer', compact)
        self.assertIn('shape = RoundedCornerShape(12.dp)', compact)
        self.assertIn('Modifier.width(30.dp)', compact)
        self.assertIn('Modifier.weight(1f).testTag("legend_label_${status.name}")', compact)
        self.assertIn('Modifier.width(30.dp).testTag("legend_symbol_${status.name}")', compact)
        self.assertIn('heightIn(min = 36.dp)', compact)
        for tag in ('condition_legend_first_row', 'condition_legend_second_row'):
            self.assertIn(f'testTag("{tag}")', compact)
        # Prevent a return to a separate card/pill for each status.
        item = compact.split('private fun LegendChip', 1)[1]
        self.assertNotIn('Surface(', item)

    def test_contextual_placement_and_home_remain_unchanged(self):
        timeline = (UI / 'citydetail/SimplifiedTimeline.kt').read_text(encoding='utf-8')
        self.assertLess(timeline.index('R.string.timeline_disagreement_hint'),
                        timeline.index('ConditionStatusLegend('))
        chart = (UI / 'graphicview/GraphicForecastScreen.kt').read_text(encoding='utf-8')
        content = chart.split('internal fun GraphicForecastContent(', 1)[1].split(
            'private fun GraphicChartPanel(', 1)[0]
        self.assertLess(content.index('GraphicChartPanel('), content.index('ConditionStatusLegend('))
        self.assertLess(content.index('ConditionStatusLegend('), content.index('OpenMeteoAttribution('))
        home = (UI / 'citylist/CityListScreen.kt').read_text(encoding='utf-8')
        self.assertNotIn('ConditionStatusLegend(', home)


if __name__ == '__main__':
    unittest.main()
