"""Test the GitHub Actions Android instrumentation report summarizer."""

import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/summarize_android_test_results.py'
spec = importlib.util.spec_from_file_location('summarize_android_test_results', SCRIPT)
reporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reporter)


class AndroidTestSummaryTests(unittest.TestCase):
    def test_parses_failures_and_preserves_diagnostic(self):
        with tempfile.TemporaryDirectory() as directory:
            d = Path(directory)
            (d / 'connected/debug').mkdir(parents=True)
            (d / 'connected/debug/TEST-ui.xml').write_text('''<?xml version="1.0"?>
<testsuite name="ui" tests="3" failures="1" errors="1" skipped="0">
  <testcase classname="com.app.WeatherTest" name="renders_city" />
  <testcase classname="com.app.WeatherTest" name="changes_hour">
    <failure message="Expected 25° but got 23°">AssertionError: wrong hour\nat WeatherTest.kt:123</failure>
  </testcase>
  <testcase classname="com.app.WeatherTest" name="error_screen">
    <error message="NullPointerException">stack trace</error>
  </testcase>
</testsuite>''', encoding='utf-8')
            tests, failures, skipped, files, warnings = reporter.read_results(d)
            self.assertEqual((tests, len(failures), skipped, len(files), len(warnings)), (3, 2, 0, 1, 0))
            summary = reporter.format_summary(d, (tests, failures, skipped, files, warnings))
            self.assertIn('changes_hour', summary)
            self.assertIn('WeatherTest.kt:123', summary)
            self.assertIn('error_screen', summary)
            self.assertIn('Expected 25°', summary)

            output = d / 'summary.md'
            proc = subprocess.run([sys.executable, str(SCRIPT), '--results', str(d), '--summary', str(output)],
                                  capture_output=True, text=True, timeout=10)
            self.assertEqual(0, proc.returncode, proc.stderr)
            self.assertIn('::error title=AndroidTest changes_hour::', proc.stdout)
            self.assertIn('changes_hour', output.read_text(encoding='utf-8'))

    def test_no_reports_is_clear_and_not_fatal(self):
        with tempfile.TemporaryDirectory() as d:
            missing = Path(d) / 'missing'
            summary = Path(d) / 'summary.md'
            proc = subprocess.run([sys.executable, str(SCRIPT), '--results', str(missing), '--summary', str(summary)],
                                  capture_output=True, text=True, timeout=10)
            self.assertEqual(0, proc.returncode, proc.stderr)
            self.assertIn('Aucun rapport XML JUnit', summary.read_text(encoding='utf-8'))

    def test_malformed_xml_ignored_and_marked(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'broken.xml'
            path.write_text('<testcase', encoding='utf-8')
            result = reporter.read_results(Path(d))
            self.assertEqual(0, result[0])
            self.assertEqual(1, len(result[4]))
            self.assertIn('Fichiers XML illisibles', reporter.format_summary(Path(d), result))

    def test_escapes_markdown_and_annotations(self):
        self.assertIn('\\|', reporter._cell('left | right'))
        self.assertNotIn('\n', reporter.annotation_escape('bad\nnewline'))
        self.assertIn('%25', reporter.annotation_escape('100%'))

    def test_workflows_publish_diagnostics_after_failed_emulator_step(self):
        for workflow in ('quality-checks.yml', 'release-fdroid.yml'):
            with self.subTest(workflow=workflow):
                text = (ROOT / '.github/workflows' / workflow).read_text(encoding='utf-8')
                self.assertIn('scripts/summarize_android_test_results.py', text)
                self.assertIn('--summary "$GITHUB_STEP_SUMMARY"', text)
                self.assertRegex(text, r'(?s)Show Android test failures in GitHub job summary.*?if: always\(\)')
                self.assertIn('if-no-files-found: warn', text)
                self.assertIn('outputs/androidTest-results/', text)


if __name__ == '__main__':
    unittest.main()
