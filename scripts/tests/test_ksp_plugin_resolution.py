"""Contrat de resolution du plugin KSP par le workflow GitHub Actions."""
from pathlib import Path
import tomllib
import unittest

ROOT = Path(__file__).resolve().parents[2]


class KspPluginResolutionTest(unittest.TestCase):
    def test_ksp_version_is_pinned_and_plugin_is_declared(self):
        versions = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8"))
        self.assertRegex(versions["versions"]["ksp"], r"^\d+\.\d+\.\d+$")
        self.assertEqual("com.google.devtools.ksp", versions["plugins"]["ksp"]["id"])

    def test_maven_central_is_preferred_for_plugin_resolution(self):
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        plugin_repos = settings.split("dependencyResolutionManagement", 1)[0]
        # Les deux entrees sont presentes dans le bloc pluginManagement, dans le bon ordre.
        self.assertIn("mavenCentral()", plugin_repos)
        self.assertIn("google {", plugin_repos)
        self.assertLess(plugin_repos.index("mavenCentral()"), plugin_repos.index("google {"))

    def test_ci_retries_only_when_ksp_plugin_resolution_fails(self):
        workflow = (ROOT / ".github/workflows/quality-checks.yml").read_text(encoding="utf-8")
        self.assertIn('grep -Fq "Plugin [id: \'com.google.devtools.ksp\'" gradle-quality.log', workflow)
        self.assertIn("--refresh-dependencies", workflow)
        self.assertIn("else\n            exit 1", workflow)
        self.assertIn("quality-gradle-failure-log", workflow)


if __name__ == "__main__":
    unittest.main()
