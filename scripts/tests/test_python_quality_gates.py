"""Exercise the checkers themselves, including negative fixtures and CLI failures.

The Python gates detect source/resource contracts; they do not replace Kotlin,
Compose or Android runtime tests.
"""

import contextlib
import importlib.util
import io
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str, module_name: str):
    spec = importlib.util.spec_from_file_location(module_name, ROOT / 'scripts' / name)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class PythonQualityGateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.previews = load_script('validate-compose-previews.py', 'compose_preview_gate')
        cls.page_size = load_script('verify-16kb-page-size.py', 'elf_page_gate_extra')
        cls.atmospheric = load_script('verify_atmospheric_localizations.py', 'atmosphere_gate_extra')

    def test_unannotated_debug_reference_must_not_count_as_a_compose_preview(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'A.kt'
            path.write_text('''
// @MeteoScreenPreview FakeComposable()
private fun unrelated() { TargetComposable() }
@Composable
private fun noPreview() { AnotherComposable() }
''', encoding='utf-8')
            self.assertEqual(set(), self.previews.preview_invocations([path]))

    def test_actual_preview_reaches_nested_debug_helper_but_not_unrelated_function(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'Previews.kt'
            path.write_text('''
@MeteoScreenPreview
@Composable
private fun ScreenPreview() { helper() }
private fun helper() { TargetComposable() }
private fun unrelated() { NotPreviewedComposable() }
''', encoding='utf-8')
            calls = self.previews.preview_invocations([path])
            self.assertIn('helper', calls)
            self.assertIn('TargetComposable', calls)
            self.assertNotIn('NotPreviewedComposable', calls)

    def test_a_preview_annotation_definition_is_not_a_preview_function(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'Annotation.kt'
            path.write_text('''
@Preview(name = "Theme")
internal annotation class MeteoScreenPreview
@Composable
fun HelperWithoutPreview() { UncoveredComposable() }
''', encoding='utf-8')
            self.assertEqual(set(), self.previews.preview_invocations([path]))

    def test_truncated_elf_header_returns_explicit_verification_error(self):
        gate = self.page_size
        for data in (b'\x7fELF\x02\x01' + b'\x00' * 10,
                     b'\x7fELF\x01\x01' + b'\x00' * 10):
            with self.subTest(elf_class=data[4]):
                with self.assertRaisesRegex(gate.VerificationError, 'truncated ELF'):
                    gate.elf_load_alignments(data, 'truncated.so')

    def test_malformed_program_header_table_rejected_without_struct_error(self):
        gate = self.page_size
        for offset, phentsize, count in ((5000, 56, 1), (64, 1, 1), (64, 56, 4)):
            with self.subTest(offset=offset, entry_size=phentsize, count=count):
                elf = bytearray(120)
                elf[:6] = b'\x7fELF\x02\x01'
                struct.pack_into('<Q', elf, 32, offset)
                struct.pack_into('<H', elf, 54, phentsize)
                struct.pack_into('<H', elf, 56, count)
                with self.assertRaisesRegex(gate.VerificationError, 'program header table'):
                    gate.elf_load_alignments(bytes(elf), 'corrupt.so')

    def test_valid_elf32_header_is_supported(self):
        gate = self.page_size
        elf = bytearray(84)
        elf[:6] = b'\x7fELF\x01\x01'
        struct.pack_into('<I', elf, 28, 52)   # Program header offset
        struct.pack_into('<H', elf, 42, 32)   # ELF32 program header size
        struct.pack_into('<H', elf, 44, 1)
        struct.pack_into('<I', elf, 52, 1)    # PT_LOAD
        struct.pack_into('<I', elf, 52 + 28, 16384)
        self.assertEqual([16384], gate.elf_load_alignments(bytes(elf), 'lib.so'))

    def test_atmospheric_localization_gate_rejects_mismatched_placeholders(self):
        gate = self.atmospheric
        with tempfile.TemporaryDirectory() as tmp:
            res = Path(tmp)
            for language in gate.LANGUAGES:
                folder = res / language
                folder.mkdir()
                strings = ''.join(
                    f'<string name="{name}">Label %1$s</string>' for name in gate.REQUIRED
                )
                (folder / 'strings.xml').write_text('<resources>' + strings + '</resources>',
                                                    encoding='utf-8')
            with patch.object(gate, 'RES', res), contextlib.redirect_stderr(io.StringIO()) as errors:
                self.assertEqual(0, gate.main())
                path = res / 'values-de' / 'strings.xml'
                path.write_text(path.read_text(encoding='utf-8').replace('Label %1$s', 'Label %1$d', 1),
                                encoding='utf-8')
                self.assertEqual(1, gate.main())
                self.assertIn('placeholder mismatch', errors.getvalue())

    def test_atmospheric_localization_gate_requires_every_translation(self):
        gate = self.atmospheric
        with tempfile.TemporaryDirectory() as tmp:
            res = Path(tmp)
            for language in gate.LANGUAGES:
                folder = res / language
                folder.mkdir()
                strings = ''.join(f'<string name="{name}">Label</string>'
                                  for name in gate.REQUIRED
                                  if not (language == 'values-it' and name == gate.REQUIRED[0]))
                (folder / 'strings.xml').write_text('<resources>' + strings + '</resources>', encoding='utf-8')
            with patch.object(gate, 'RES', res), contextlib.redirect_stderr(io.StringIO()) as errors:
                self.assertEqual(1, gate.main())
                self.assertIn('must appear exactly once', errors.getvalue())

    def test_verify_shell_rejects_invalid_arguments_before_gradle(self):
        result = subprocess.run(['bash', str(ROOT / 'scripts/verify.sh'), '--invalid'],
                                cwd='/', capture_output=True, text=True, timeout=10)
        self.assertEqual(2, result.returncode)
        self.assertIn('Usage:', result.stderr)
        self.assertNotIn('gradlew', result.stdout)

    def test_release_ci_executes_all_unittest_style_python_guards(self):
        workflow = (ROOT / '.github/workflows/release-fdroid.yml').read_text(encoding='utf-8')
        self.assertIn("python3 -m unittest discover -s scripts/tests -p 'test_*.py'", workflow)
        self.assertIn('python3 scripts/verify_atmospheric_localizations.py', workflow)
        # All test functions live inside unittest.TestCase classes; otherwise
        # `unittest discover` would silently skip those functions.
        for file in (ROOT / 'scripts/tests').glob('test_*.py'):
            source = file.read_text(encoding='utf-8')
            self.assertNotRegex(source, r'(?m)^def test_\w+\s*\(', file.name)


if __name__ == '__main__':
    unittest.main()
