"""CI summary tests must never treat missing/failed jobs as passing."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'report_test_results.py'
spec = importlib.util.spec_from_file_location('ci_test_report', SCRIPT)
report = importlib.util.module_from_spec(spec)
import sys
sys.modules[spec.name] = report
spec.loader.exec_module(report)


def junit(directory, name, clazz, state='passed', receipt=None):
    directory.mkdir(parents=True, exist_ok=True)
    node = '' if state == 'passed' else ('<skipped/>' if state == 'skipped'
                                        else '<failure message="test failed">stack trace</failure>')
    path = directory / f'TEST-{clazz}.xml'
    output = f'<system-out>{receipt}</system-out>' if receipt is not None else ''
    path.write_text(f'<testsuite name="{clazz}"><testcase classname="com.test.{clazz}" '
                    f'name="{name}">{node}</testcase>{output}</testsuite>', encoding='utf-8')


class GitHubTestSummaryTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.counts = report.read_corpus_manifest(
            Path(__file__).resolve().parents[2] /
            'app/src/test/resources/weather_regression/v1/manifest.json')

    def tearDown(self):
        self.tmp.cleanup()

    def test_missing_xml_never_claims_the_corpus_or_tests_passed(self):
        suite = report.scan_junit(self.root / 'missing')
        self.assertEqual(suite.total, 0)
        self.assertEqual(report.corpus_status(suite, self.counts)['validated'], 0)
        self.assertEqual(report.corpus_status(suite, self.counts)['not_verified'], 74537)
        output = report.render({'debug':suite, 'release':suite, 'android':suite},
                               {'debug': report.corpus_status(suite,self.counts),
                                'release': report.corpus_status(suite,self.counts)}, None)
        self.assertIn('Non exécuté', output)

    def test_passed_scenario_method_counts_fixture_rows_not_single_junit_test(self):
        clazz, method = report.CORPUS_CONSUMERS['clouds.tsv']
        junit(self.root, method, clazz, receipt=(
            f'METEOCOMPARE_CORPUS_V1 file=clouds.tsv cases={self.counts["clouds.tsv"]} elapsed_ms=123'))
        suite = report.scan_junit(self.root)
        self.assertEqual((suite.total,suite.passed), (1,1))
        corpus = report.corpus_status(suite, self.counts)
        self.assertEqual(corpus['validated'], self.counts['clouds.tsv'])
        self.assertEqual(corpus['not_verified'], 74537 - self.counts['clouds.tsv'])

    def test_failed_corpus_suite_is_not_misreported_as_passed(self):
        clazz, method = report.CORPUS_CONSUMERS['sunshine_consensus.tsv']
        junit(self.root, method, clazz, 'failed')
        suite = report.scan_junit(self.root)
        counts = report.corpus_status(suite, self.counts)
        self.assertEqual(counts['validated'], 0)
        self.assertEqual(counts['affected_by_failing_test'], 5120)
        self.assertEqual(len(suite.failures), 1)

    def test_other_unit_test_failure_not_counted_as_corpus(self):
        junit(self.root, 'ordinaryTest', 'OtherTest', 'failed')
        suite = report.scan_junit(self.root)
        self.assertEqual((suite.failed, suite.total), (1, 1))
        self.assertEqual(report.corpus_status(suite, self.counts)['validated'], 0)

    def test_skips_and_malformed_xml_are_not_success(self):
        junit(self.root, 'ordinaryTest', 'OtherTest', 'skipped')
        (self.root / 'TEST-invalid.xml').write_text('<broken>', encoding='utf-8')
        suite = report.scan_junit(self.root)
        self.assertEqual(suite.skipped, 1)
        self.assertEqual(suite.passed, 0)
        self.assertEqual(len(suite.warnings), 1)

    def test_python_log_distinguishes_success_failure_and_absence(self):
        self.assertIsNone(report.python_status(self.root / 'absent'))
        path = self.root / 'python.txt'
        path.write_text('Ran 195 tests in 1.23s\n\nOK (skipped=2)\n')
        self.assertEqual(report.python_status(path)['passed'], 193)
        path.write_text('Ran 195 tests in 1.23s\n\nFAILED (failures=2, errors=1, skipped=3)\n')
        self.assertEqual(report.python_status(path)['failed'], 3)

    def test_all_nine_successful_corpus_consumers_validate_all_scenarios(self):
        rows = ''.join(
            f'<testcase classname="com.test.{klass}" name="{method}" />'
            for klass, method in report.CORPUS_CONSUMERS.values()
        )
        receipts = ''.join(
            f'METEOCOMPARE_CORPUS_V1 file={file} cases={self.counts[file]} elapsed_ms=1\n'
            for file in report.CORPUS_CONSUMERS
        )
        (self.root / 'TEST-corpus.xml').write_text(
            f'<testsuite name="corpus">{rows}<system-out>{receipts}</system-out></testsuite>',
            encoding='utf-8')
        result = report.scan_junit(self.root)
        self.assertEqual((result.total, result.passed), (9, 9))
        corpus = report.corpus_status(result, self.counts)
        self.assertEqual(corpus['validated'], 74537)
        self.assertEqual(corpus['not_verified'], 0)

    def test_passing_junit_without_execution_receipt_proves_zero_scenarios(self):
        clazz, method = report.CORPUS_CONSUMERS['clouds.tsv']
        junit(self.root, method, clazz)
        suite = report.scan_junit(self.root)
        self.assertEqual(suite.passed, 1)
        self.assertEqual(report.corpus_status(suite, self.counts)['validated'], 0)

    def test_mismatched_or_duplicate_receipts_cannot_prove_corpus(self):
        clazz, method = report.CORPUS_CONSUMERS['clouds.tsv']
        good = f'METEOCOMPARE_CORPUS_V1 file=clouds.tsv cases={self.counts["clouds.tsv"]} elapsed_ms=7'
        for receipts in (good.replace('cases=40411', 'cases=40410'), good + '\n' + good):
            with self.subTest(receipts=receipts):
                junit(self.root, method, clazz, receipt=receipts)
                suite = report.scan_junit(self.root)
                self.assertEqual(report.corpus_status(suite, self.counts)['validated'], 0)

    def test_wrong_class_receipt_is_rejected(self):
        good = f'METEOCOMPARE_CORPUS_V1 file=clouds.tsv cases={self.counts["clouds.tsv"]} elapsed_ms=7'
        junit(self.root, 'other', 'OtherTest', receipt=good)
        suite = report.scan_junit(self.root)
        self.assertEqual(suite.receipts, {})
        self.assertTrue(suite.warnings)

    def test_required_proof_cli_fails_if_junit_output_is_missing(self):
        import subprocess
        repo = Path(__file__).resolve().parents[2]
        cmd = [sys.executable, 'scripts/report_test_results.py', '--debug',
               str(self.root / 'missing'), '--require-corpus', 'debug']
        result = subprocess.run(cmd, cwd=repo, text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('CORPUS EXECUTION NOT PROVEN', result.stdout)

    def test_instrumented_test_counts_are_read_from_xml(self):
        rows = ('<testcase classname="x.TodaySummaryCardTest" name="cloudTest" />'
                '<testcase classname="x.TodaySummaryCardTest" name="failedTest">'
                '<failure message="Missing view" /></testcase>'
                '<testcase classname="x.TodaySummaryCardTest" name="skippedTest">'
                '<skipped/></testcase>')
        (self.root / 'TEST-android.xml').write_text(
            f'<testsuite name="Android">{rows}</testsuite>', encoding='utf-8')
        result = report.scan_junit(self.root)
        self.assertEqual((result.total, result.passed, result.failed, result.skipped),
                         (3, 1, 1, 1))

    def test_both_workflows_keep_reporting_after_test_failures(self):
        repo = Path(__file__).resolve().parents[2]
        for workflow in ('quality-checks.yml', 'release-fdroid.yml'):
            with self.subTest(workflow=workflow):
                data = (repo / '.github/workflows' / workflow).read_text(encoding='utf-8')
                title = ('name: Consolidated test report' if workflow == 'quality-checks.yml'
                         else 'name: Release validation — consolidated test report')
                self.assertIn(title, data)
                self.assertIn('if: always()', data)
                self.assertIn('scripts/report_test_results.py', data)
                self.assertIn('actions/download-artifact@v4', data)

    def test_manifest_is_complete_and_consumer_mapping_is_one_to_one(self):
        self.assertEqual(sum(self.counts.values()), 74537)
        self.assertEqual(len(set(report.CORPUS_CONSUMERS.values())), len(self.counts))


if __name__ == '__main__':
    unittest.main()
