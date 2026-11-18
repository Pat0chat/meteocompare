"""Catalogue des modèles, tel que défini dans l'application.

Miroir de WeatherModel.kt (clé API, couverture, horizon) et de
ForecastConsensus.groupFor (lignée). scripts/tests/test_backtest_catalogue.py
compare ce fichier au code Kotlin : toute dérive casse le test.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Tuple


@dataclass(frozen=True)
class Model:
    name: str
    api_key: str
    group: str
    coverage: str
    max_forecast_days: int


MODELS: Tuple[Model, ...] = (
    Model('AROME_FRANCE_HD', 'meteofrance_arome_france_hd', 'MF_AROME', 'FRANCE', 3),
    Model('AROME_FRANCE', 'meteofrance_arome_france', 'MF_AROME', 'FRANCE', 3),
    Model('ARPEGE_EUROPE', 'meteofrance_arpege_europe', 'MF_ARPEGE', 'EUROPE', 4),
    Model('ARPEGE_WORLD', 'meteofrance_arpege_world', 'MF_ARPEGE', 'GLOBAL', 4),
    Model('ICON_EU', 'icon_eu', 'DWD_ICON', 'EUROPE', 5),
    Model('ICON_GLOBAL', 'icon_global', 'DWD_ICON', 'GLOBAL', 8),
    Model('GFS', 'ncep_gfs_seamless', 'NOAA_GFS', 'GLOBAL', 16),
    Model('ECMWF', 'ecmwf_ifs', 'ECMWF_GLOBAL', 'GLOBAL', 15),
    Model('UKMO_GLOBAL', 'ukmo_global_deterministic_10km', 'UKMO_GLOBAL', 'GLOBAL', 7),
    Model('ECMWF_AIFS', 'ecmwf_aifs025_single', 'ECMWF_GLOBAL', 'GLOBAL', 15),
    Model('GEM_GLOBAL', 'cmc_gem_gdps', 'ECCC_GEM', 'GLOBAL', 10),
    Model('ICON_D2', 'icon_d2', 'DWD_ICON', 'EUROPE', 3),
    Model('HRRR_CONUS', 'ncep_hrrr_conus', 'NOAA_HRRR', 'UNITED_STATES', 3),
    Model('METNO_NORDIC', 'metno_nordic', 'METNO_NORDIC', 'EUROPE', 4),
    Model('KNMI_HARMONIE_EU', 'knmi_harmonie_arome_europe', 'UWC_HARMONIE', 'EUROPE', 4),
    Model('BOM_ACCESS', 'bom_access_global', 'BOM_ACCESS', 'GLOBAL', 10),
    Model('CMA_GRAPES', 'cma_grapes_global', 'CMA_GRAPES', 'GLOBAL', 10),
    Model('DMI_HARMONIE_EU', 'dmi_harmonie_arome_europe', 'UWC_HARMONIE', 'EUROPE', 4),
    Model('METEOSWISS_ICON_CH2', 'meteoswiss_icon_ch2', 'DWD_ICON', 'EUROPE', 5),
    Model('JMA_GSM', 'jma_gsm', 'JMA_GSM', 'GLOBAL', 11),
    Model('NCEP_AIGFS', 'ncep_aigfs025', 'NOAA_GFS', 'GLOBAL', 16),
    Model('CHMI_ALADIN_CENTRAL_EUROPE', 'chmi_aladin_central_europe_2km', 'CHMI_ALADIN', 'EUROPE', 3),
)

BY_NAME: Dict[str, Model] = {m.name: m for m in MODELS}
BY_API_KEY: Dict[str, Model] = {m.api_key: m for m in MODELS}

# Sélection activée par défaut dans l'app (WeatherModel.MVP_SELECTION).
DEFAULT_SELECTION: Tuple[str, ...] = (
    'AROME_FRANCE_HD', 'ARPEGE_EUROPE', 'ICON_EU', 'GFS', 'ECMWF',
    'UKMO_GLOBAL', 'ECMWF_AIFS', 'JMA_GSM', 'NCEP_AIGFS',
)


def group_for(model: str) -> str:
    return BY_NAME[model].group
