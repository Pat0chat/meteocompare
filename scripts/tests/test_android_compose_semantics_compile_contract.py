"""Guard against unsupported Compose UI-test APIs in instrumented tests.

The project uses Compose SemanticsConfiguration's indexing operator to read the
GetTextLayoutResult action. getOrNull() is not available on that type, and
SemanticsNodeInteraction.assertExists() is a member, not a top-level import.
This structural check complements the AndroidTest compilation in CI.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
ANDROID_TEST = ROOT / "app/src/androidTest"
UNSUPPORTED_GET_LAYOUT = re.compile(
    r"\.getOrNull\s*\(\s*SemanticsActions\.GetTextLayoutResult\s*\)"
)
UNSUPPORTED_ASSERT_EXISTS_IMPORT = re.compile(
    r"^\s*import\s+androidx\.compose\.ui\.test\.assertExists\s*$",
    re.MULTILINE,
)
# Kotlin treats a bracket starting a new statement after a line ending in
# `.config` as an array literal, not a call to SemanticsConfiguration.get().
# Keep the dot and index access together; a line break before `.config` is safe.
UNSUPPORTED_SPLIT_SEMANTICS_INDEX = re.compile(
    r"\.config[ \t]*\r?\n[ \t]*\[\s*SemanticsActions\.GetTextLayoutResult\s*\]"
)


class AndroidComposeSemanticsCompilationContract(unittest.TestCase):
    def test_no_unsupported_get_or_null_on_semantics_configuration(self):
        sources = list(ANDROID_TEST.rglob("*.kt"))
        self.assertTrue(sources, "Instrumented Kotlin tests must exist")
        for source in sources:
            with self.subTest(source=source.relative_to(ROOT)):
                self.assertNotRegex(source.read_text(encoding="utf-8"), UNSUPPORTED_GET_LAYOUT)

    def test_semantics_index_is_not_split_after_config(self):
        sources = list(ANDROID_TEST.rglob("*.kt"))
        self.assertTrue(sources, "Instrumented Kotlin tests must exist")
        for source in sources:
            with self.subTest(source=source.relative_to(ROOT)):
                self.assertNotRegex(
                    source.read_text(encoding="utf-8"),
                    UNSUPPORTED_SPLIT_SEMANTICS_INDEX,
                )

    def test_semantics_index_guard_rejects_regression_but_allows_valid_code(self):
        broken = "val action = node.fetchSemanticsNode().config\n    [SemanticsActions.GetTextLayoutResult].action"
        fixed = "val action = node.fetchSemanticsNode()\n    .config[SemanticsActions.GetTextLayoutResult].action"
        self.assertRegex(broken, UNSUPPORTED_SPLIT_SEMANTICS_INDEX)
        self.assertNotRegex(fixed, UNSUPPORTED_SPLIT_SEMANTICS_INDEX)

    def test_assert_exists_is_not_imported_as_a_top_level_extension(self):
        sources = list(ANDROID_TEST.rglob("*.kt"))
        self.assertTrue(sources, "Instrumented Kotlin tests must exist")
        for source in sources:
            with self.subTest(source=source.relative_to(ROOT)):
                self.assertNotRegex(
                    source.read_text(encoding="utf-8"), UNSUPPORTED_ASSERT_EXISTS_IMPORT
                )


if __name__ == "__main__":
    unittest.main()
