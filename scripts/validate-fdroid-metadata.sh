#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_FILE="$ROOT/app/build.gradle.kts"
META="$ROOT/fastlane/metadata/android"

version_name="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$BUILD_FILE" | head -n1)"
version_code="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$BUILD_FILE" | head -n1)"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
ok()   { printf 'OK: %s\n' "$*"; }

[[ -n "$version_name" ]] || fail "Could not read versionName from app/build.gradle.kts"
[[ -n "$version_code" ]] || fail "Could not read versionCode from app/build.gradle.kts"
ok "Android version is ${version_name} (${version_code})"

python3 - "$META" "$version_code" <<'PY'
from pathlib import Path
import struct
import sys

meta = Path(sys.argv[1])
code = sys.argv[2]
limits = {
    "title.txt": 50,
    "short_description.txt": 80,
    "full_description.txt": 4000,
}
locales = [p for p in meta.iterdir() if p.is_dir()]
if not locales:
    raise SystemExit("ERROR: no Fastlane locales found")

for loc in sorted(locales):
    for name, limit in limits.items():
        p = loc / name
        if not p.exists():
            raise SystemExit(f"ERROR: missing {p}")
        text = p.read_text(encoding="utf-8").strip()
        if not text:
            raise SystemExit(f"ERROR: empty {p}")
        if len(text) > limit:
            raise SystemExit(f"ERROR: {p} is {len(text)} chars (max {limit})")
        print(f"OK: {loc.name}/{name}: {len(text)}/{limit} chars")

    ch = loc / "changelogs" / f"{code}.txt"
    if not ch.exists():
        raise SystemExit(f"ERROR: missing changelog for versionCode {code}: {ch}")
    txt = ch.read_text(encoding="utf-8").strip()
    if len(txt) > 500:
        raise SystemExit(f"ERROR: {ch} is {len(txt)} chars (max 500)")
    print(f"OK: {loc.name}/changelogs/{code}.txt: {len(txt)}/500 chars")

    icon = loc / "images" / "icon.png"
    feature = loc / "images" / "featureGraphic.png"
    def png_size(path):
        with path.open("rb") as f:
            header = f.read(24)
        if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
            raise SystemExit(f"ERROR: {path} is not a valid PNG")
        return struct.unpack(">II", header[16:24])

    if icon.exists():
        size = png_size(icon)
        if size != (512, 512):
            raise SystemExit(f"ERROR: {icon} is {size}, expected 512x512")
        print(f"OK: {loc.name} icon is 512x512")
    if feature.exists():
        size = png_size(feature)
        if size != (1024, 500):
            raise SystemExit(f"ERROR: {feature} is {size}, expected 1024x500")
        print(f"OK: {loc.name} feature graphic is 1024x500")
PY

# Basic static checks for the bootstrap fdroiddata recipe. It intentionally
# stays anchored to the initial accepted build, so it must NOT be forced to
# match every future app version.
YML="$ROOT/fdroid/com.meteocompare.app.yml"
[[ -f "$YML" ]] || fail "Missing $YML"
grep -q '^RepoType: git$' "$YML" || fail "fdroiddata RepoType must be git"
grep -q '^AutoUpdateMode: Version$' "$YML" || fail "fdroiddata AutoUpdateMode should be Version"
grep -q '^UpdateCheckMode:' "$YML" || fail "fdroiddata UpdateCheckMode missing"
ok "bootstrap fdroiddata recipe has the expected update policy"

printf '\nAll local F-Droid metadata checks passed.\n'
