"""Protect the reorder wiring and prevent accidental loss of GPS order or translations."""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAIN = ROOT / 'app/src/main/java/com/meteocompare/app'


class HomeCityReorderContractTest(unittest.TestCase):
    def test_home_exposes_editor_and_saves_via_viewmodel(self):
        screen = (MAIN / 'ui/citylist/CityListScreen.kt').read_text(encoding="utf-8")
        viewmodel = (MAIN / 'ui/citylist/CityListViewModel.kt').read_text(encoding="utf-8")
        self.assertIn('home_reorder_action', screen)
        self.assertIn('CityReorderSheet(', screen)
        self.assertIn('viewModel.onReorderHomeCities(ids)', screen)
        self.assertIn('cityRepository.reorderFavorites(cityIds)', viewmodel)

    def test_storage_is_distinct_from_favorites_and_gps_id_is_stable(self):
        repository = (MAIN / 'data/repository/CityRepositoryImpl.kt').read_text(encoding="utf-8")
        order = (MAIN / 'domain/util/FavoriteCityOrder.kt').read_text(encoding="utf-8")
        self.assertIn('stringPreferencesKey("home_city_order")', repository)
        self.assertIn('FavoriteCityOrder.apply(allCities, savedOrder, City::id)', repository)
        self.assertIn('prefs[HOME_ORDER_KEY] =', repository)
        self.assertIn('saved.filterNot { it == cityId }', repository)
        self.assertIn('LocationPrivacyGrid.isCellId(id)', order)
        self.assertIn('fun normalize(', order)

    def test_reorder_editor_has_draggable_handle_and_accessible_arrows(self):
        source = (MAIN / 'ui/citylist/CityReorderSheet.kt').read_text(encoding="utf-8")
        self.assertIn('detectDragGesturesAfterLongPress', source)
        self.assertIn('TAG_REORDER_UP', source)
        self.assertIn('TAG_REORDER_DOWN', source)
        self.assertIn('onSave(order.toList())', source)

    def test_all_localizations_define_reorder_labels(self):
        keys = {'home_reorder_title', 'home_reorder_hint', 'home_reorder_up',
                'home_reorder_down', 'home_reorder_drag', 'home_reorder_apply',
                'home_reorder_save_error'}
        for folder in ('values', 'values-en', 'values-de', 'values-es', 'values-it', 'values-tr'):
            with self.subTest(locale=folder):
                root = ET.parse(ROOT / 'app/src/main/res' / folder / 'strings.xml').getroot()
                strings = {e.attrib.get('name'): e.text for e in root.findall('string')}
                self.assertTrue(keys <= strings.keys())
                self.assertTrue(all(strings[k] for k in keys))


if __name__ == '__main__':
    unittest.main()
