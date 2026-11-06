"""Low-cost Kotlin source hygiene checks (no Android SDK required).

These checks catch deterministic issues only. They do not attempt to replace
Kotlin/Compose compilation or infer unused public APIs.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
KOTLIN_SOURCES = ROOT / "app" / "src"
# Kotlin delegated-state property access invokes these imported operators
# implicitly, so their names need not appear in a source file body.
IMPLICIT_OPERATOR_IMPORTS = {"getValue", "setValue", "provideDelegate"}


class KotlinSourceHygieneTest(unittest.TestCase):
    def test_no_duplicate_kotlin_imports(self):
        duplicates = []
        for source in KOTLIN_SOURCES.rglob("*.kt"):
            imports = [line.strip() for line in source.read_text(encoding="utf-8").splitlines()
                       if line.startswith("import ")]
            seen = set()
            for imported in imports:
                if imported in seen:
                    duplicates.append(f"{source.relative_to(ROOT)}: {imported}")
                seen.add(imported)
        self.assertEqual([], duplicates)

    def test_no_obviously_unused_explicit_kotlin_imports(self):
        unused = []
        for source in KOTLIN_SOURCES.rglob("*.kt"):
            lines = source.read_text(encoding="utf-8").splitlines()
            body = "\n".join(line for line in lines if not line.startswith("import "))
            for line in lines:
                if not line.startswith("import "):
                    continue
                imported = line.removeprefix("import ").strip()
                if imported.endswith(".*"):
                    continue
                name = imported.split(" as ")[-1].rsplit(".", 1)[-1]
                if name in IMPLICIT_OPERATOR_IMPORTS:
                    continue
                if not re.search(r"\b" + re.escape(name) + r"\b", body):
                    unused.append(f"{source.relative_to(ROOT)}: {imported}")
        self.assertEqual([], unused)


if __name__ == "__main__":
    unittest.main()
