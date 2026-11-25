#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Reject unsupported options before running any checks or building an APK.
if [[ "${1:-}" != "" && "${1:-}" != "--connected" ]] || [[ "$#" -gt 1 ]]; then
    printf 'Usage: %s [--connected]\n' "$0" >&2
    exit 2
fi

python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
python3 scripts/generate_weather_regression_corpus.py --check
python3 scripts/generate_unit_conversion_corpus.py --check
python3 scripts/generate_condition_status_corpus.py --check
python3 scripts/generate_temporal_windows_corpus.py --check
python3 scripts/generate_civil_day_coverage_corpus.py --check
python3 scripts/validate-compose-previews.py
python3 scripts/check_android_strings.py
python3 scripts/test-summary-dialog-copy.py
python3 scripts/verify_atmospheric_localizations.py
bash scripts/validate-fdroid-metadata.sh

./gradlew testDebugUnitTest testReleaseUnitTest lintDebug assembleDebug
python3 scripts/report_test_results.py --require-corpus both

if [[ "${1:-}" == "--connected" ]]; then
    ./gradlew connectedDebugAndroidTest
fi
