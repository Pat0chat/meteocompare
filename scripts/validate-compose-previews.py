#!/usr/bin/env python3
"""Validate that visual Compose sources have debug-only previews.

The navigation host is intentionally excluded: it only wires Hilt-backed destinations
and has no visual surface of its own. Previewing it would instantiate production
ViewModels inside Android Studio preview.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN_UI = ROOT / "app/src/main/java/com/meteocompare/app/ui"
DEBUG_UI = ROOT / "app/src/debug/java/com/meteocompare/app/ui"
EXCLUDED = {
    "navigation/AppNavHost.kt": "navigation wiring only; Hilt destinations have their own stateless previews",
}


def non_private_composables(path: Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    result: list[str] = []
    for index, line in enumerate(lines):
        if "@Composable" not in line:
            continue
        for candidate in lines[index + 1:index + 14]:
            match = re.match(r"\s*(?:(internal|public|private)\s+)?fun\s+([A-Za-z0-9_]+)\s*\(", candidate)
            if match:
                visibility = match.group(1) or "public"
                if visibility != "private":
                    result.append(match.group(2))
                break
    return list(dict.fromkeys(result))



# A plain mention of a composable in a debug file (including in comments or
# fixture helpers) is not a preview. Only calls reachable from a real preview
# function count, directly or through debug-only helper functions.
PREVIEW_FUNCTION = re.compile(
    r"(?m)^[ \t]*@(?:Preview|MeteoScreenPreview|MeteoComponentPreview)\b[^\n]*"
    r"(?:\([^)]*\))?[ \t]*(?:\n[ \t]*@[A-Za-z_][\w.]*[^\n]*[ \t]*)*"
    r"\n[ \t]*(?:private|internal|public)?[ \t]*fun[ \t]+(\w+)\s*\("
)
FUNCTION_DECLARATION = re.compile(
    r"(?m)^[ \t]*(?:(?:private|internal|public|override|suspend|inline)\s+)*"
    r"fun[ \t]+(\w+)\s*\("
)
CALL = re.compile(r"\b([A-Za-z_]\w*)\s*\(")


def strip_comments(source: str) -> str:
    """Ignore source references hidden in Kotlin comments during preview checks."""
    return re.sub(r"(?m)//[^\n]*$", "", re.sub(r"/\*.*?\*/", "", source, flags=re.S))


def preview_invocations(debug_sources: list[Path]) -> set[str]:
    """Names called by annotated previews, following debug helper functions."""
    functions: dict[str, str] = {}
    preview_names: set[str] = set()
    for path in debug_sources:
        source = strip_comments(path.read_text(encoding="utf-8"))
        declarations = list(FUNCTION_DECLARATION.finditer(source))
        for idx, match in enumerate(declarations):
            end = declarations[idx + 1].start() if idx + 1 < len(declarations) else len(source)
            functions[match.group(1)] = source[match.end():end]
        preview_names.update(match.group(1) for match in PREVIEW_FUNCTION.finditer(source))

    seen: set[str] = set()
    invoked: set[str] = set()
    pending = list(preview_names)
    while pending:
        name = pending.pop()
        if name in seen:
            continue
        seen.add(name)
        body = functions.get(name, "")
        calls = set(CALL.findall(body))
        invoked.update(calls)
        pending.extend(calls & functions.keys() - seen)
    return invoked


def main() -> int:
    main_previews = list(MAIN_UI.rglob("*.kt"))
    leaked = [p for p in main_previews if "@Preview" in p.read_text(encoding="utf-8")]
    if leaked:
        print("ERROR: @Preview must stay in src/debug/java:")
        for path in leaked:
            print(" -", path.relative_to(ROOT))
        return 1

    preview_calls = preview_invocations(list(DEBUG_UI.rglob("*.kt")))
    if not preview_calls:
        print("ERROR: no real annotated Compose previews found in src/debug/java")
        return 1
    missing: list[tuple[Path, list[str]]] = []
    covered = 0

    for source in sorted(MAIN_UI.rglob("*.kt")):
        names = non_private_composables(source)
        if not names:
            continue
        rel = source.relative_to(MAIN_UI).as_posix()
        if rel in EXCLUDED:
            continue
        if any(name in preview_calls for name in names):
            covered += 1
        else:
            missing.append((source, names))

    if missing:
        print("ERROR: visual Compose files without debug preview coverage:")
        for source, names in missing:
            print(f" - {source.relative_to(ROOT)} ({', '.join(names)})")
        return 1

    print(f"OK: {covered} visual Compose source files covered by debug previews.")
    for rel, reason in EXCLUDED.items():
        print(f"EXCLUDED: {rel} — {reason}")
    print("OK: no @Preview annotation is shipped from src/main/java.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
