"""Structure de la CI : étapes mutualisées, jobs parallèles, publication protégée."""
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.ci_workflows import (  # noqa: E402
    WORKFLOWS, all_workflow_and_action_files, job_needs, local_targets, top_level_jobs)


def read(name: str) -> str:
    return (WORKFLOWS / name).read_text(encoding='utf-8')


class SharedStepsTest(unittest.TestCase):
    def test_every_local_workflow_or_action_exists(self):
        for path in all_workflow_and_action_files():
            for target in local_targets(path.read_text(encoding='utf-8')):
                with self.subTest(caller=path.name, target=str(target.relative_to(ROOT))):
                    self.assertTrue(target.is_file())

    def test_android_sdk_setup_lives_only_in_the_shared_action(self):
        for path in all_workflow_and_action_files():
            text = path.read_text(encoding='utf-8')
            with self.subTest(file=str(path.relative_to(ROOT))):
                if path.name == 'action.yml' and path.parent.name == 'setup-android':
                    self.assertIn('sdkmanager', text)
                else:
                    self.assertNotIn('sdkmanager', text)
                    self.assertNotIn('actions/setup-java@', text)

    def test_python_checks_device_tests_and_report_are_shared(self):
        for workflow in ('quality-checks.yml', 'release-fdroid.yml'):
            text = read(workflow)
            for shared in ('reusable-python-checks.yml', 'reusable-android-device-tests.yml',
                           'reusable-test-report.yml'):
                with self.subTest(workflow=workflow, shared=shared):
                    self.assertIn(f'uses: ./.github/workflows/{shared}', text)


class ParallelismTest(unittest.TestCase):
    def test_quality_checks_start_together_and_report_waits_for_all(self):
        jobs = top_level_jobs(read('quality-checks.yml'))
        for job in ('python', 'android-jvm', 'android-device'):
            with self.subTest(job=job):
                self.assertEqual([], job_needs(jobs[job]))
        self.assertEqual({'python', 'android-jvm', 'android-device'}, set(job_needs(jobs['test-report'])))
        self.assertIn('if: always()', jobs['test-report'])

    def test_device_tests_are_sharded_without_overwriting_reports(self):
        device = read('reusable-android-device-tests.yml')
        self.assertIn('numShards=${{ strategy.job-total }}', device)
        self.assertIn('shardIndex=${{ strategy.job-index }}', device)
        self.assertIn('name: ${{ inputs.artifact-name }}-shard-${{ strategy.job-index }}', device)
        self.assertIn('fail-fast: false', device)
        report = read('reusable-test-report.yml')
        self.assertIn('pattern: ${{ inputs.android-artifact-prefix }}-shard-*', report)

    def test_release_builds_in_parallel_but_publishes_only_after_every_gate(self):
        text = read('release-fdroid.yml')
        jobs = top_level_jobs(text)
        self.assertEqual([], job_needs(jobs['build-apk']))
        self.assertEqual({'python', 'android-device-gate', 'build-apk'}, set(job_needs(jobs['publish'])))
        self.assertIn("if: github.event_name == 'push' && github.ref_type == 'tag'", jobs['publish'])

    def test_only_the_publish_job_can_write_to_the_repository(self):
        text = read('release-fdroid.yml')
        header = text.split('\njobs:\n', 1)[0]
        self.assertIn('permissions:\n  contents: read', header)
        jobs = top_level_jobs(text)
        writers = [name for name, body in jobs.items() if 'contents: write' in body]
        self.assertEqual(['publish'], writers)

    def test_reproducible_release_build_keeps_its_isolated_gradle_invocation(self):
        build = top_level_jobs(read('release-fdroid.yml'))['build-apk']
        self.assertIn("gradle-cache: 'false'", build)
        self.assertIn("validate-wrapper: 'true'", build)
        for flag in ('--no-build-cache', '--no-configuration-cache', '--max-workers=1', 'clean'):
            with self.subTest(flag=flag):
                self.assertIn(flag, build)


if __name__ == '__main__':
    unittest.main()
