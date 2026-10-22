"""Contracts for Windows Python UTF-8, main-thread disk access and Glance Row limits.

These source checks complement, but do not replace, instrumented device tests.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/meteocompare/app"


class WindowsUtf8StrictModeGlanceTest(unittest.TestCase):
    def test_all_script_read_text_calls_declare_utf8(self):
        # On French Windows (cp1252), Kotlin contains valid UTF-8 bytes invalid
        # for the locale's default decoder. Audit all test and utility scripts.
        for script in (ROOT / "scripts").rglob("*.py"):
            with self.subTest(script=script.relative_to(ROOT)):
                source = script.read_text(encoding="utf-8")
                self.assertNotRegex(source, r"\.read_text\(\s*\)")

    def test_shared_preferences_never_initialized_on_ui_thread(self):
        offer = (JAVA / "data/repository/LocationPermissionOffer.kt").read_text(encoding="utf-8")
        home = (JAVA / "ui/citylist/CityListScreen.kt").read_text(encoding="utf-8")
        widget = (JAVA / "widget/WidgetLocationRefreshActivity.kt").read_text(encoding="utf-8")
        self.assertIn("import kotlinx.coroutines.withContext", offer)
        for name in ("shouldRequest", "markPrecisionOffered", "recordPermissionResult"):
            signature = re.search(r"suspend fun " + name + r"\([\s\S]*?\)\s*(?::\s*Boolean\s*)?=\s*withContext\(Dispatchers.IO\)", offer)
            self.assertIsNotNone(signature, name)
        self.assertIn("permissionScope.launch", home)
        self.assertIn("permissionUiBusy", home)
        self.assertIn("lifecycleScope.launch", widget)
        self.assertIn("private suspend fun launchLocationPermissionRequest()", widget)
        self.assertIn("Lifecycle.State.STARTED", widget)

    def test_five_forecast_and_confidence_items_each_use_one_parent_child(self):
        widget = (JAVA / "widget/MeteoWidget.kt").read_text(encoding="utf-8")
        forecast = widget.split("private fun ForecastTimelinePanel(", 1)[1].split("private fun ColumnScope.CombinedConfidenceBands(", 1)[0]
        confidence = widget.split("private fun ModernConfidencePanel(", 1)[1].split("private fun ConfidenceDayCard(", 1)[0]
        for name, source, marker in (
            ("forecast", forecast, "visibleItems.forEachIndexed { index, item ->"),
            ("confidence", confidence, "days.forEachIndexed { index, day ->"),
        ):
            with self.subTest(row=name):
                children = source.split(marker, 1)[1]
                self.assertIn("Row(modifier = GlanceModifier.defaultWeight().fillMaxHeight())", children)
                self.assertLess(children.index("Row(modifier = GlanceModifier.defaultWeight().fillMaxHeight())"),
                                children.index("if (index > 0)"))
        layout = (JAVA / "widget/WidgetForecastCardLayout.kt").read_text(encoding="utf-8")
        self.assertIn("itemCount.coerceIn(0, MAX_GLANCE_FORECAST_TIMELINE_ITEMS)", layout)


if __name__ == "__main__":
    unittest.main()
