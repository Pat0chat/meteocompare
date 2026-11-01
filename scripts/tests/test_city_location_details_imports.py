"""Guards cross-package usage of the shared city details composable.

A missing Kotlin import compiles neither in the debug build nor in production;
static validation catches the regression even without an Android SDK.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app" / "src" / "main" / "java"
DEBUG = ROOT / "app" / "src" / "debug" / "java"
COMPONENT_PACKAGE = "com.meteocompare.app.ui.components"
COMPONENT_NAMES = ("CityLocationDetails", "CityHomeLocationDetails", "CitySearchLocationDetails")


class CityLocationDetailsImportTest(unittest.TestCase):
    def test_shared_component_exists(self):
        source = MAIN / "com/meteocompare/app/ui/components/CityLocationDetails.kt"
        self.assertTrue(source.is_file())
        code = source.read_text(encoding="utf-8")
        for name in COMPONENT_NAMES:
            self.assertIn(f"fun {name}(", code)

    def test_every_cross_package_usage_imports_component(self):
        users = {name: [] for name in COMPONENT_NAMES}
        for source_root in (MAIN, DEBUG):
            if not source_root.is_dir():
                continue
            for source in source_root.rglob("*.kt"):
                contents = source.read_text(encoding="utf-8")
                package = re.search(r"^package\s+([\w.]+)", contents, re.MULTILINE)
                self.assertIsNotNone(package, str(source))
                for name in COMPONENT_NAMES:
                    if not re.search(rf"\b{name}\s*\(", contents):
                        continue
                    if package.group(1) == COMPONENT_PACKAGE:
                        continue
                    users[name].append(source.relative_to(ROOT).as_posix())
                    self.assertTrue(
                        f"import {COMPONENT_PACKAGE}.{name}" in contents or
                        f"{COMPONENT_PACKAGE}.{name}(" in contents,
                        f"Missing {name} import in {source.relative_to(ROOT)}",
                    )
        for name, paths in users.items():
            self.assertTrue(paths, f"Expected a cross-package usage of {name}")

    def test_home_uses_two_line_component_in_both_card_variants(self):
        home = (MAIN / "com/meteocompare/app/ui/citylist/CityListScreen.kt").read_text(encoding="utf-8")
        self.assertEqual(2, len(re.findall(r"\bCityHomeLocationDetails\s*\(", home)))
        self.assertIn('"home_city_location_compact_${state.city.id}"', home)
        self.assertIn('"home_city_location_${city.id}"', home)
        self.assertNotIn("CityLocationDetails(", home)

    def test_detail_header_uses_single_line_subtitle(self):
        detail = (MAIN / "com/meteocompare/app/ui/citydetail/CityDetailScreen.kt").read_text(encoding="utf-8")
        self.assertIn('details.joinToString(" · ")', detail)
        self.assertRegex(
            detail,
            r'(?s)details\.joinToString\(" · "\).*?'
            r'testTag\(TAG_DETAIL_CITY_LOCATION\).*?maxLines = 1',
        )
        self.assertNotIn("CityLocationDetails(", detail)

    def test_home_header_keeps_title_and_location_together(self):
        home = (MAIN / "com/meteocompare/app/ui/citylist/CityListScreen.kt").read_text(encoding="utf-8")
        # The title measures at its natural width (capped at 52%), while the
        # location FILLs the entire remaining width. Former fill=false weights
        # caused a large empty area on the right on some phone sizes.
        # Read BoxWithConstraints.maxWidth before entering the nested Row scope:
        # resolving it directly inside Text's Modifier is a Kotlin compile error.
        self.assertEqual(2, home.count("val titleMaxWidth = maxWidth * 0.52f"))
        self.assertEqual(2, home.count("Modifier.widthIn(max = titleMaxWidth)"))
        self.assertNotIn("Modifier.widthIn(max = maxWidth * 0.52f)", home)
        self.assertIn('Modifier.weight(1f).testTag("home_city_location_${city.id}")', home)
        self.assertRegex(home, r'Modifier\.weight\(1f\)\s*\.testTag\("home_city_location_compact_')
        self.assertIn('"home_city_header_${city.id}"', home)
        self.assertIn('"home_city_header_compact_${state.city.id}"', home)
        self.assertNotIn("Modifier.weight(1.2f, fill = false)", home)
        self.assertNotIn("Modifier.weight(1f, fill = false)", home)

    def test_chart_and_meteogram_headers_only_display_city_name(self):
        meteogram = (MAIN / "com/meteocompare/app/ui/meteogram/MeteogramScreen.kt").read_text(encoding="utf-8")
        chart = (MAIN / "com/meteocompare/app/ui/graphicview/GraphicForecastScreen.kt").read_text(encoding="utf-8")
        self.assertIn("state.city.name", meteogram)
        self.assertNotIn("CityLocationDetails(", meteogram)
        self.assertNotIn("meteogram_city_location", meteogram)
        self.assertIn("cityName = state.city.name", chart)
        self.assertNotIn("citySubtitle", chart)
        self.assertNotIn("state.city.locationDetails", chart)
        self.assertIn("Text(cityName,", chart)

    def test_search_has_two_lines_and_home_uses_smaller_type(self):
        search = (MAIN / "com/meteocompare/app/ui/citylist/AddCitySheet.kt").read_text(encoding="utf-8")
        shared = (MAIN / "com/meteocompare/app/ui/components/CityLocationDetails.kt").read_text(encoding="utf-8")
        self.assertIn('CitySearchLocationDetails(city = city, modifier = Modifier.testTag(', search)
        self.assertIn('import com.meteocompare.app.ui.components.CitySearchLocationDetails', search)
        self.assertIn('val lines = city.homeLocationLines', shared)
        self.assertIn('maxLines = 1', shared)
        self.assertIn('fontSize = 10.sp, lineHeight = 12.sp', shared)

    def test_compose_layout_test_uses_public_semantics_action(self):
        test_source = (ROOT / "app/src/androidTest/java/com/meteocompare/app/ui/citylist/CityListContentTest.kt").read_text(encoding="utf-8")
        self.assertNotIn("androidx.compose.ui.test.getTextLayoutResult", test_source)
        self.assertIn("SemanticsActions.GetTextLayoutResult", test_source)


if __name__ == "__main__":
    unittest.main()
