"""Regression guard for non-format rainfall explanations in all translations."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res"
LANGUAGES = ("values", "values-en", "values-de", "values-es", "values-it")
PERCENT_TEXT_KEYS = ("metric_calc_rain_rule", "metric_calc_rain_dry_rounding", "metric_calc_rain_no_amount")


class RainExplanationResourceTest(unittest.TestCase):
    def test_all_literal_percent_explanations_are_nonformatted(self):
        for language in LANGUAGES:
            strings = ET.parse(ROOT / language / "strings.xml").getroot()
            entries = {node.attrib.get("name"): node for node in strings.findall("string")}
            for key in PERCENT_TEXT_KEYS:
                with self.subTest(language=language, key=key):
                    text = entries[key].text or ""
                    self.assertIn("%", text)
                    self.assertEqual("false", entries[key].attrib.get("formatted"))

    def test_spanish_and_italian_plurals_cover_many_quantity(self):
        for language in ("values-es", "values-it"):
            strings = ET.parse(ROOT / language / "strings.xml").getroot()
            plurals = {n.attrib["name"]: n for n in strings.findall("plurals")}
            forms = {item.attrib["quantity"]: item.text for item in plurals["metric_summary_models_count"]}
            with self.subTest(language=language):
                self.assertTrue({"one", "many", "other"}.issubset(forms))
                self.assertTrue(all(forms[quantity].startswith("%d ") for quantity in ("one", "many", "other")))


if __name__ == "__main__":
    unittest.main()
