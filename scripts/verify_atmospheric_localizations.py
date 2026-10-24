#!/usr/bin/env python3
"""Verify the five resource sets used by feels-like and pressure UI.

Run from any directory: python3 scripts/verify_atmospheric_localizations.py
The Android instrumentation tests are still required to verify on-device layout.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
LANGUAGES = ("values", "values-en", "values-de", "values-es", "values-it")
REQUIRED = (
    "metric_apparent_temperature",
    "metric_atmospheric_pressure",
    "metric_pressure_short",
    "today_apparent_min_short",
    "today_apparent_max_short",
    "today_apparent_min_accessibility",
    "today_apparent_max_accessibility",
)
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sdf]")


def main() -> int:
    errors = []
    canonical_placeholders = {}
    for language in LANGUAGES:
        path = RES / language / "strings.xml"
        try:
            strings = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as error:
            errors.append(f"{language}: invalid XML: {error}")
            continue
        for name in REQUIRED:
            matches = [item for item in strings if item.tag == "string" and item.get("name") == name]
            if len(matches) != 1:
                errors.append(f"{language}: {name} must appear exactly once")
                continue
            value = "".join(matches[0].itertext()).strip()
            if not value:
                errors.append(f"{language}: empty {name}")
            placeholders = sorted(PLACEHOLDER.findall(value))
            if name in canonical_placeholders and canonical_placeholders[name] != placeholders:
                errors.append(f"{language}: placeholder mismatch for {name}")
            else:
                canonical_placeholders[name] = placeholders
        # Compact pressure labels must fit the narrow overview/chronology columns.
        compact = next((el.text or "" for el in strings
                        if el.tag == "string" and el.get("name") == "metric_pressure_short"), "")
        if len(compact) > 12:
            errors.append(f"{language}: pressure label too long: {compact!r}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"OK: {len(REQUIRED)} new metric labels in {len(LANGUAGES)} languages; "
          "unique, nonempty, and placeholder-compatible")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
