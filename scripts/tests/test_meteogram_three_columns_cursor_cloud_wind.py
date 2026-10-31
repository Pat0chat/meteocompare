"""Regression guards for the incremental 1.19.3 meteogram UX patch."""
from pathlib import Path
import unittest

SRC = (Path(__file__).resolve().parents[2] /
       "app/src/main/java/com/meteocompare/app/ui/meteogram/MeteogramScreen.kt")

class MeteogramSelectedUXTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SRC.read_text(encoding="utf-8")

    def test_three_columns_for_all_selected_value_cards(self):
        grid = self.source.split("private fun MeteogramSelectedMetricContent(", 1)[1].split(
            "private fun MeteogramValuePill(", 1)[0]
        self.assertIn("metrics.chunked(3)", grid)
        self.assertIn("repeat(3 - rowMetrics.size)", grid)
        self.assertEqual(2, self.source.count("MeteogramSelectedMetricContent(") - 1)

    def test_time_badge_is_attached_to_shared_chart_cursor(self):
        track = self.source.split("private fun MeteogramSelectedTimeTrack(", 1)[1].split(
            "private fun MeteogramCardSelectedValues(", 1)[0]
        self.assertIn("(selectedIndex + 0.5f) * step - offset", track)
        self.assertIn("meteogramSelectedTimeBadgeLeft(cursorX", track)
        self.assertIn("WeatherTimeFormat.clock(locale, clockPreference)", track)
        self.assertIn("selected.instant.atZone(zone)", track)
        self.assertEqual(2, self.source.count(
            "MeteogramSelectedTimeTrack(hours, selectedIndex, offset, step, zone, locale)"))

    def test_cloud_legend_has_only_three_altitude_layers_with_percent(self):
        legend = self.source.split("private fun MeteogramCloudRainLegend(", 1)[1].split(
            "private fun MeteogramCombinedLegend(", 1)[0]
        self.assertNotIn("meteogram_cloud_picker", legend)
        for altitude in ("low", "mid", "high"):
            self.assertIn(f"R.string.meteogram_cloud_{altitude}", legend)
            self.assertIn(f"meteogram_cloud_{altitude})}} (%)", legend)
        self.assertEqual(5, legend.count("LegendEntry("))

    def test_all_in_one_shows_wind_direction_on_plot_and_values(self):
        combined = self.source.split("private fun MeteogramCombinedCard(", 1)[1].split(
            "private fun MeteogramCloudLayersHint(", 1)[0]
        self.assertIn("cardinalLanguage = locale.language", combined)
        values = self.source.split("private fun MeteogramSelectedValues(", 1)[1].split(
            "private fun MeteogramSelectedMetricContent(", 1)[0]
        self.assertIn("R.string.meteogram_wind_origin", values)
        self.assertIn("meteogramWindCardinal(hour.windDirectionDeg, locale.language)", values)
        self.assertIn('"$cardinal · ${hour.windDirectionDeg}°"', values)

if __name__ == "__main__":
    unittest.main()
