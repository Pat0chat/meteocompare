"""Keep the five privacy-policy translations and their navigation in sync.

Run with: python3 -m unittest scripts.tests.test_privacy_translations
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
POLICIES = (
    "PRIVACY.md",  # French source of truth
    "PRIVACY.en.md",
    "PRIVACY.de.md",
    "PRIVACY.es.md",
    "PRIVACY.it.md",
)
PERMISSIONS = (
    "INTERNET",
    "ACCESS_NETWORK_STATE",
    "RECEIVE_BOOT_COMPLETED",
    "POST_NOTIFICATIONS",
    "ACCESS_COARSE_LOCATION",
    "ACCESS_FINE_LOCATION",
    "ACCESS_BACKGROUND_LOCATION",
)
TECHNICAL_TERMS = (
    "AD_ID",
    "Open-Meteo",
    "Météo-France",
    "RainViewer",
    "OpenStreetMap",
    "DataStore",
    "SharedPreferences",
    "Room SQLite",
    "Glance",
    "cacheDir",
    "current_location",
    "GPS_PROVIDER",
    "coast=1",
    "64 MiB",
    "CC BY 4.0",
)
EXTERNAL_LINKS = (
    "https://open-meteo.com/",
    "https://open-meteo.com/en/terms",
    "https://open-meteo.com/en/license",
    "https://github.com/Pat0chat/MeteoCompare/issues",
)


class PrivacyTranslationsTest(unittest.TestCase):
    def test_all_language_links_are_reciprocal(self):
        for name in POLICIES:
            with self.subTest(document=name):
                text = (ROOT / name).read_text(encoding="utf-8")
                relative_links = re.findall(r"\[[^\]]+\]\((PRIVACY(?:\.[a-z]{2})?\.md)\)", text)
                self.assertEqual(set(relative_links), set(POLICIES))
                self.assertEqual(len(relative_links), len(POLICIES))
                for link in relative_links:
                    self.assertTrue((ROOT / link).is_file(), link)

    def test_all_documents_preserve_sections_and_tables(self):
        for name in POLICIES:
            with self.subTest(document=name):
                text = (ROOT / name).read_text(encoding="utf-8")
                section_numbers = re.findall(r"^## (\d+)\. ", text, re.MULTILINE)
                self.assertEqual(section_numbers, [str(x) for x in range(1, 12)])
                self.assertEqual(len(re.findall(r"^\|.*\|$", text, re.MULTILINE)), 18)
                self.assertGreaterEqual(len(re.findall(r"^## ", text, re.MULTILINE)), 12)

    def test_essential_legal_and_technical_details_remain_present(self):
        for name in POLICIES:
            with self.subTest(document=name):
                text = (ROOT / name).read_text(encoding="utf-8")
                for term in PERMISSIONS + TECHNICAL_TERMS + EXTERNAL_LINKS:
                    self.assertIn(term, text)
                self.assertIn("90", text)
                self.assertIn("30", text)
                self.assertTrue("0,05" in text or "0.05" in text)
                self.assertIn("v1.19.0", text)
                self.assertIn("https://meteocompare.app/_mcx/vigilance", text)
                self.assertIn("https://open-meteo.com/en/terms", text)


if __name__ == "__main__":
    unittest.main()
