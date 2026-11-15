"""Regression checks for the Node.js 24 GitHub Actions migration.

No additional YAML parser dependency is needed in the Python CI job.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / '.github' / 'workflows'
EXPECTED_ACTIONS = {
    'actions/checkout': 'v7',
    'actions/setup-java': 'v6',
    'actions/setup-node': 'v7',
    'actions/upload-artifact': 'v7',
    'actions/download-artifact': 'v7',
    'gradle/actions/setup-gradle': 'v6',
    'gradle/actions/wrapper-validation': 'v6',
}


class GithubActionsNode24Test(unittest.TestCase):
    def test_all_official_actions_use_node24_compatible_major_versions(self):
        encountered = set()
        for workflow in sorted(WORKFLOWS.glob('*.yml')):
            data = workflow.read_text(encoding='utf-8')
            for action, major in re.findall(
                    r'^\s*(?:-\s*)?uses:\s*([\w-]+/(?:[\w-]+/)?[\w-]+)@(v\d+)\s*$',
                    data, re.MULTILINE):
                if action not in EXPECTED_ACTIONS:
                    continue  # Third-party actions have their own upgrade policy.
                encountered.add(action)
                with self.subTest(workflow=workflow.name, action=action):
                    self.assertEqual(major, EXPECTED_ACTIONS[action])
        self.assertEqual(encountered, set(EXPECTED_ACTIONS))

    def test_sponsors_script_uses_node24_and_does_not_require_new_dependencies(self):
        data = (WORKFLOWS / 'update-sponsors.yml').read_text(encoding='utf-8')
        self.assertIn('actions/setup-node@v7', data)
        self.assertRegex(data, r'node-version:\s*[\'\"]24[\'\"]')
        self.assertIn('node scripts/update-sponsors.mjs', data)

    def test_quality_workflow_preserves_tests_and_artifact_names(self):
        data = (WORKFLOWS / 'quality-checks.yml').read_text(encoding='utf-8')
        for expected in (
                'scripts/generate_weather_regression_corpus.py --check',
                ':app:testDebugUnitTest :app:testReleaseUnitTest',
                ':app:connectedDebugAndroidTest',
                'name: quality-unit-test-results',
                'name: androidTest-reports',
                'name: quality-python-test-log',
                'name: quality-consolidated-test-report',
                'actions/download-artifact@v7',
                'if: always()'):
            with self.subTest(expected=expected):
                self.assertIn(expected, data)

    def test_release_gates_and_signed_artifacts_remain_intact(self):
        data = (WORKFLOWS / 'release-fdroid.yml').read_text(encoding='utf-8')
        for expected in (
                ':app:connectedDebugAndroidTest',
                ':app:bundleRelease',
                ':app:lintDebug',
                'apksigner', 'jarsigner',
                'Create or update GitHub Release',
                'name: release-python-test-log',
                'name: release-unit-test-results',
                'name: release-androidTest-reports',
                'name: release-consolidated-test-report'):
            with self.subTest(expected=expected):
                self.assertIn(expected, data)


if __name__ == '__main__':
    unittest.main()
