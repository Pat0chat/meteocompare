pluginManagement {
    // Les modules de performance utilisent le plugin Android de test.
    // On récupère automatiquement la version AGP déjà déclarée dans le
    // version catalog afin d'éviter toute divergence avec :app.
    val versionCatalog = settingsDir.resolve("gradle/libs.versions.toml")
    val versionCatalogText = versionCatalog
        .takeIf { it.isFile }
        ?.readText()
        ?: error("gradle/libs.versions.toml est requis pour configurer les modules de performance")

    fun pluginVersion(pluginId: String): String {
        val entry = Regex(
            """(?m)^\s*[A-Za-z0-9_.-]+\s*=\s*\{[^}]*id\s*=\s*"${Regex.escape(pluginId)}"[^}]*}\s*$"""
        ).find(versionCatalogText)?.value
            ?: error("Plugin $pluginId introuvable dans gradle/libs.versions.toml")

        Regex("""version\s*=\s*"([^"]+)"""")
            .find(entry)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        val versionRef = Regex("""version\.ref\s*=\s*"([^"]+)"""")
            .find(entry)
            ?.groupValues
            ?.get(1)
            ?: error("Aucune version/version.ref trouvée pour $pluginId")

        return Regex("""(?m)^\s*${Regex.escape(versionRef)}\s*=\s*"([^"]+)"\s*$""")
            .find(versionCatalogText)
            ?.groupValues
            ?.get(1)
            ?: error("Version '$versionRef' introuvable dans gradle/libs.versions.toml")
    }

    plugins {
        id("com.android.test") version pluginVersion("com.android.application")
        id("androidx.baselineprofile") version "1.5.0"
    }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MeteoCompare"
include(":app")
include(":baselineprofile")
include(":benchmark")
