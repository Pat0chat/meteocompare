import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ─── Signing config : lit keystore.properties si présent, sinon env vars (CI) ──
//
// Trois modes de signature supportés :
//   1. Développement local : `keystore.properties` à la racine du projet
//      (gitignored). Le release est signé avec ce keystore.
//   2. CI release : variables d'environnement KEYSTORE_PATH, KEYSTORE_PASSWORD,
//      KEY_ALIAS, KEY_PASSWORD. Le workflow GitHub décode un secret base64.
//   3. Pas de config : `assembleRelease` produit un APK NON SIGNÉ (utile pour
//      tester le shrinking sans avoir besoin d'un keystore).
//
// L'objectif : on ne committe JAMAIS le keystore ni les credentials, et il n'y
// a pas d'erreur de build si le développeur veut juste compiler en debug.

val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasKeystoreProperties = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasKeystoreProperties) keystorePropertiesFile.inputStream().use { load(it) }
}

fun signingValue(key: String, envKey: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(envKey)

android {
    namespace = "com.meteocompare.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.meteocompare.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 10
        versionName = "1.0.0"
        testInstrumentationRunner = "com.meteocompare.app.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signingValue("storeFile", "KEYSTORE_PATH")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signing config appliqué uniquement si la storeFile a été configurée.
            // Sinon assembleRelease produit un APK non signé, ce qui est OK pour
            // tester le shrinking en local sans secret.
            val releaseSigningConfig = signingConfigs.getByName("release")
            if (releaseSigningConfig.storeFile != null) {
                signingConfig = releaseSigningConfig
            }
            // Symboles de débogage natifs.
            //
            // L'app n'embarque pas de code natif propre, mais ses dépendances
            // androidx (graphics.path, datastore.shared_counter) embarquent des
            // .so prébuilts. Sans cette config, le Play Console affiche le
            // warning "App Bundle contient du code natif, vous n'avez pas
            // importé de symboles de débogage" à chaque upload.
            //
            // `FULL` (vs `SYMBOL_TABLE`) : on prend tout ce qu'AGP peut
            // extraire — les prébuilts ayant été strippés à la source, le ZIP
            // produit est petit, donc autant être complet pour le peu qu'il y a.
            // Le fichier `native-debug-symbols.zip` se retrouve à côté de l'AAB
            // dans `app/build/outputs/bundle/release/` et s'uploade au Play.
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // `kotlinOptions { jvmTarget = "17" }` est déprécié depuis KGP 2.0 ;
    // le bloc `kotlin { compilerOptions { ... } }` au niveau projet (en
    // dehors d'`android { }`) le remplace — voir plus bas.

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Nécessaire pour appeler android.util.Log dans le code de production
    // depuis un unit test JVM pur — sans `returnDefaultValues = true`,
    // Log.i/d/w renvoient une RuntimeException "Method not mocked" qui casse
    // tout test qui déclenche du code loggé. Avec ce flag, les méthodes
    // Android non implémentées retournent une valeur par défaut (0, null,
    // false) au lieu de crasher — comportement sûr pour du logging accessoire.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Évite le warning "Unable to strip the following libraries" lors du
        // build sans NDK installé. Ces .so prébuilts viennent de dépendances
        // androidx (graphics.path, datastore.shared_counter) qui sont déjà
        // strippées à la source — la tentative d'AGP de re-stripper échoue
        // silencieusement et émet un warning. On dit explicitement "garde-les
        // tels quels" pour silencier le warning sans changer le comportement.
        jniLibs.keepDebugSymbols += setOf(
            "**/libandroidx.graphics.path.so",
            "**/libdatastore_shared_counter.so"
        )
    }

    // Pour Play Store : bundle obligatoire depuis 2021.
    // assembleRelease produit toujours un APK pour tests locaux, bundleRelease
    // produit l'AAB pour upload.
    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
}

// Configuration Kotlin niveau projet — remplace `kotlinOptions { }`
// (déprécié depuis KGP 2.0). Le type sûr `JvmTarget.JVM_17` est préféré à la
// chaîne `"17"` parce qu'il échoue à la compilation Gradle si la valeur est
// invalide, plutôt qu'à l'exécution.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.bundles.network)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ─── Widget homescreen (Glance) ───────────────────────────────────────
    // Glance permet d'écrire les widgets en Composable-like au lieu de
    // RemoteViews XML brut. En interne, Glance recompile en RemoteViews —
    // les mêmes contraintes s'appliquent : pas de Modifier générique, pas
    // de callbacks arbitraires (uniquement Action), API très restreinte.
    // Le sur-ensemble de Compose disponible est documenté dans le package
    // `androidx.glance.*`. On garde `glance-material3` pour hériter du
    // thème Material 3 côté widget (couleurs primaires du système).
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // ─── WorkManager ──────────────────────────────────────────────────────
    // Remplace le refresh AlarmManager historique du widget (updatePeriodMillis)
    // par des jobs planifiés qui respectent Doze/App Standby/Battery Saver +
    // supportent des contraintes réseau et batterie. Voir [WidgetRefreshScheduler]
    // pour les détails de motivation et la configuration des contraintes.
    //
    // Pas de hilt-work : on utilise EntryPointAccessors pour injecter les
    // dépendances dans le worker (même pattern que le widget lui-même). Plus
    // léger qu'@HiltWorker + @AssistedInject qui demanderait un artefact
    // supplémentaire (androidx.hilt:hilt-work) et une config Application
    // custom implémentant Configuration.Provider.
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
