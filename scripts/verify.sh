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
python3 scripts/validate-compose-previews.py
python3 scripts/check_android_strings.py
python3 scripts/test-summary-dialog-copy.py
python3 scripts/verify_atmospheric_localizations.py
scripts/validate-fdroid-metadata.sh

./gradlew testDebugUnitTest lintDebug assembleDebug

if [[ "${1:-}" == "--connected" ]]; then
    ./gradlew connectedDebugAndroidTest
fi
