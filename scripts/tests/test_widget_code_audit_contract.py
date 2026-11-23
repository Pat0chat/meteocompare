"""Fast CI safeguards for the weather and insight widget lifecycle.

These are source-structure checks; the behaviour itself is covered by the
Kotlin/Android tests and the manual launcher test matrix.
"""
from pathlib import Path
import unittest

WIDGETS = Path(__file__).resolve().parents[2] / "app/src/main/java/com/meteocompare/app/widget"


class WidgetCodeAuditContractTest(unittest.TestCase):
    def test_city_reconfiguration_never_keeps_previous_city_snapshot(self):
        weather = (WIDGETS / "MeteoWidget.kt").read_text(encoding="utf-8")
        insight = (WIDGETS / "MeteoValueWidgets.kt").read_text(encoding="utf-8")
        self.assertIn("remember(cityId, forecastMode)", weather)
        self.assertIn("remember(cityId)", insight)
        self.assertIn("LaunchedEffect(cityId, forecastMode, refreshTick)", weather)
        self.assertIn("LaunchedEffect(cityId, refreshTick)", insight)

    def test_cancelled_owner_does_not_poison_a_live_widget_waiter(self):
        source = (WIDGETS / "WidgetDataSingleFlight.kt").read_text(encoding="utf-8")
        self.assertIn("currentCoroutineContext().ensureActive()", source)
        self.assertIn("result.await()", source)
        self.assertIn("existing !== cancelledOwner", source)
        self.assertIn("pending[key] === result", source)
        self.assertIn("NonCancellable", source)

    def test_widget_forecast_has_no_obsolete_single_model_builders(self):
        source = (WIDGETS / "WidgetData.kt").read_text(encoding="utf-8")
        self.assertIn("internal fun buildForecasts(", source)
        for dead_name in ("buildHourlyForecasts", "buildDailyForecasts", "dailyCloudCoverPct"):
            self.assertNotIn(dead_name, source)

    def test_heatmap_does_not_depend_on_unused_profile_anchor_catalogue(self):
        renderer = (WIDGETS / "WidgetHeatmapForecastRenderer.kt").read_text(encoding="utf-8")
        trend = (WIDGETS / "WidgetHeatmapTrendForecastRenderer.kt").read_text(encoding="utf-8")
        for source in (renderer, trend):
            self.assertIn("widgetHeatmapLabelIndices(widthPx)", source)
        self.assertNotIn("fun anchorIndices(profile:", renderer)


if __name__ == "__main__":
    unittest.main()
