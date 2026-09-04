#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 scripts/validate-compose-previews.py
scripts/validate-fdroid-metadata.sh

./gradlew testDebugUnitTest lintDebug assembleDebug

if [[ "${1:-}" == "--connected" ]]; then
    ./gradlew connectedDebugAndroidTest
elif [[ -n "${1:-}" ]]; then
    printf 'Usage: %s [--connected]\n' "$0" >&2
    exit 2
fi
