"""Source-level regression checks for chronology convergence help and heatmap.

These are fast checks for CI without Android. UI behavior/geometry is tested
separately by DailyRangeLayoutComposeTest on an Android emulator or device.
"""
from pathlib import Path
import unittest
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'app/src/main/java/com/meteocompare/app/ui/citydetail'
COLUMNS = (UI / 'SimplifiedTimeline.kt').read_text(encoding='utf-8')
CHRONO = (UI / 'ChronoTimeline.kt').read_text(encoding='utf-8')
RANGE = (UI / 'DailyRangeLabel.kt').read_text(encoding='utf-8')


class ChronologyConvergenceInfoHeatmapContract(unittest.TestCase):
    def test_information_is_in_dialog_only_and_dismissible(self):
        card = COLUMNS.split('internal fun SimplifiedTimelineCard(', 1)[1].split(
            '/** Shared explanation for the convergence score', 1)[0]
        dialog = COLUMNS.split('private fun TimelineConvergenceInfoDialog(', 1)[1].split(
            '/** Lightweight, card-free', 1)[0]
        self.assertNotIn('R.string.timeline_disagreement_hint', card)
        self.assertIn('if (expanded && showConvergenceInfo)', card)
        self.assertIn('onDismissRequest = onDismiss', dialog)
        self.assertIn('Text(stringResource(R.string.timeline_disagreement_hint))', dialog)
        self.assertIn('TAG_TIMELINE_CONVERGENCE_INFO_CLOSE', dialog)

    def test_both_layouts_forward_taps_to_the_same_popup(self):
        self.assertGreaterEqual(COLUMNS.count('onConvergenceClick = { showConvergenceInfo = true }'), 2)
        self.assertIn('TimelineConsensus(point, onConvergenceClick)', COLUMNS)
        self.assertIn('TAG_TIMELINE_CONSENSUS_BADGE', COLUMNS)
        self.assertIn('TAG_TIMELINE_DIVERGENCE_REASON', COLUMNS)
        self.assertIn('ChronoAgreementLane(points = points, onConvergenceClick = onConvergenceClick)', CHRONO)
        self.assertIn('interactionTag = TAG_TIMELINE_CHRONO_CONVERGENCE_TITLE', CHRONO)
        self.assertIn('TAG_TIMELINE_CHRONO_CONVERGENCE_CELL', CHRONO)
        self.assertIn('onClickLabel = openInfoLabel', CHRONO)

    def test_daily_heatmap_has_one_height_regardless_of_ranges(self):
        heatmap = COLUMNS.split('private fun TemperatureHeatmapBand(', 1)[1].split(
            'private fun PrecipitationHeatIndicator(', 1)[0]
        self.assertIn('.height(if (mode == DisplayMode.DAILY) 64.dp else 46.dp)', heatmap)
        self.assertNotIn('70.dp', heatmap)
        self.assertEqual(heatmap.count('singleLine = showDailyRanges'), 2)
        self.assertIn('if (singleLine)', RANGE)
        self.assertIn('weight(1f, fill = false)', RANGE)
        self.assertIn('TextOverflow.Ellipsis', RANGE)

    def test_convergence_dialog_resources_are_localized(self):
        resources = ROOT / 'app/src/main/res'
        for folder in ('values', 'values-en', 'values-de', 'values-es', 'values-it', 'values-tr'):
            with self.subTest(language=folder):
                entries = {entry.attrib.get('name'): entry.text for entry in ElementTree.parse(
                    resources / folder / 'strings.xml').getroot().findall('string')}
                for name in ('timeline_convergence_info_title',
                             'timeline_convergence_open_info', 'timeline_disagreement_hint'):
                    self.assertTrue(entries.get(name), (folder, name))


if __name__ == '__main__':
    unittest.main()
