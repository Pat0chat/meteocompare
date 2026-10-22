"""Regression contracts for inline compact vigilance and foreground-only GPS button."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = (ROOT / 'app/src/main/java/com/meteocompare/app/ui/citylist/CityListScreen.kt').read_text(encoding="utf-8")
VM = (ROOT / 'app/src/main/java/com/meteocompare/app/ui/citylist/CurrentLocationViewModel.kt').read_text(encoding="utf-8")
CTRL = (ROOT / 'app/src/main/java/com/meteocompare/app/data/repository/CurrentLocationController.kt').read_text(encoding="utf-8")


class CompactLocationContract(unittest.TestCase):
    def test_vigilance_in_header_before_convergence(self):
        header = UI.split('val agreementText = agreement?.let', 1)[0].rsplit('Row(verticalAlignment = Alignment.CenterVertically)', 1)[-1]
        self.assertIn('VigilanceCompactLevelIndicator(', header)
        self.assertIn('TAG_CITY_CARD_COMPACT_VIGILANCE', header)
        self.assertNotIn('vigilanceLevel?.let { level ->\n            Row(modifier = Modifier.fillMaxWidth()', UI)

    def test_only_percentage_shown_but_accessibility_retained(self):
        agreement = UI.split('val agreementText = agreement?.let', 1)[1].split('Icons.Default.KeyboardArrowDown', 1)[0]
        self.assertIn('text = agreementText', agreement)
        self.assertIn('R.string.home_agreement_label', agreement)
        self.assertNotIn('text = stringResource(R.string.home_agreement_label)', agreement)

    def test_location_only_button_is_not_global_refresh(self):
        self.assertIn('onCurrentLocationRefresh = { requestCurrentLocation(true) }', UI)
        self.assertIn('if (refreshLocationOnlyAfterPermission) locationVm.refreshLocationOnly()', UI)
        self.assertIn('fun refreshLocationOnly() = requestPosition(allowEnable = false)', VM)
        self.assertIn('controller.refreshExisting()', VM)
        code = VM.split('private fun requestPosition(allowEnable: Boolean)', 1)[1].split('fun refreshActive(', 1)[0]
        self.assertNotIn('onRefreshAll', code)
        self.assertNotIn('refreshActive(', code)
        self.assertIn('if (oldId != newId || updateWidgetsOnSameCell)', CTRL)

    def test_weather_timestamp_is_below_gps_only_for_current_location(self):
        footer = UI.split('internal fun HomeWeatherFooter(', 1)[1].split('AnimatedVisibility(', 1)[0]
        self.assertIn('if (locationUpdatedAt == null && lastUpdated != null)', footer)
        self.assertIn('if (locationUpdatedAt != null && lastUpdated != null)', footer)
        gps_position = footer.index('modifier = Modifier.testTag(TAG_HOME_POSITION_REFRESH)')
        weather_position = footer.index('if (locationUpdatedAt != null && lastUpdated != null)')
        self.assertGreater(weather_position, gps_position)
        self.assertIn('.fillMaxWidth()', footer[weather_position:])
        self.assertIn('.testTag(TAG_HOME_WEATHER_UPDATED)', footer[weather_position:])
        self.assertNotIn('maxLines = 1', footer[weather_position:])
        self.assertIn('modifier = Modifier.weight(1f).testTag(TAG_HOME_WEATHER_UPDATED)', footer[:gps_position])

    def test_stored_cell_timestamp_used_without_raw_fix(self):
        self.assertIn('controller.locationState', VM)
        self.assertIn('locationState.updatedAt.takeIf', UI)
        self.assertIn('CurrentLocationStore.isCurrentCity(state.city.id)', UI)
        self.assertIn('Instant.ofEpochMilli(locationUpdatedAt)', UI)
        self.assertNotIn('location.latitude', UI)
        self.assertNotIn('location.longitude', UI)


if __name__ == '__main__':
    unittest.main()
