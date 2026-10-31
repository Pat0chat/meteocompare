"""Source contract regression checks for precise solar times and panel sizes."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SCREEN = ROOT / "app/src/main/java/com/meteocompare/app/ui/meteogram/MeteogramScreen.kt"


class SolarPrecisionAndHeightsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.src = SCREEN.read_text(encoding="utf-8")

    def test_exact_solar_minutes_follow_local_clock_preference(self):
        axis = self.src.split("private fun MeteogramTimeAxis(", 1)[1].split("private fun MeteogramPlot(", 1)[0]
        self.assertIn("val clockPreference = LocalClockFormatPreference.current", axis)
        self.assertIn("WeatherTimeFormat.clock(locale, clockPreference)", axis)
        self.assertIn("marker.instant.atZone(zone).format(solarClockFormatter)", axis)
        self.assertNotIn("marker.instant.atZone(zone).format(hourFormatter)", axis)
        self.assertIn("val symbol = if (marker.type == MeteogramMarkerType.SUNRISE)", axis)

    def test_timeline_is_shorter_and_plots_gain_height(self):
        expected = {"Weather": 244, "Temperature": 228, "Humidity": 188,
                    "Pressure": 188, "Wind": 218, "Combined": 454, "Time": 49}
        for name, value in expected.items():
            with self.subTest(name=name):
                self.assertIn(f"private val {name}Height = {value}.dp", self.src)

    def test_no_redundant_cloud_layers_sentence_any_language(self):
        hint = self.src.split("private fun MeteogramCloudLayersHint(", 1)[1].split("private fun meteogramChoiceName(", 1)[0]
        self.assertIn("if (!available)", hint)
        self.assertNotIn("R.string.meteogram_cloud_layers_hint", hint)
        self.assertIn("R.string.meteogram_cloud_layers_unavailable", hint)
        for strings in (ROOT / "app/src/main/res").glob("values*/strings.xml"):
            with self.subTest(locale=str(strings.parent.name)):
                tags = {element.attrib["name"] for element in ET.parse(strings).getroot() if "name" in element.attrib}
                self.assertNotIn("meteogram_cloud_layers_hint", tags)
                self.assertIn("meteogram_cloud_layers_unavailable", tags)


if __name__ == "__main__":
    unittest.main()
