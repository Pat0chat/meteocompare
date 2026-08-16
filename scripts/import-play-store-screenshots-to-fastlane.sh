#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/play-store/screenshots-placeholders"
META="$ROOT/fastlane/metadata/android"

[[ -d "$SRC" ]] || {
  echo "No $SRC directory found; nothing to import." >&2
  exit 1
}

copy_locale() {
  local prefix="$1" locale="$2"
  local dest="$META/$locale/images/phoneScreenshots"
  mkdir -p "$dest"
  rm -f "$dest"/*.png
  local n=1 f
  while IFS= read -r f; do
    cp "$f" "$dest/$n.png"
    n=$((n + 1))
  done < <(find "$SRC" -maxdepth 1 -type f -name "${prefix}_*.png" | sort -V)
  echo "Imported $((n - 1)) screenshots for $locale"
}

copy_locale en en-US
copy_locale fr fr-FR
