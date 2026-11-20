import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.aboutlibraries)
}

// Release contract shared with .github/workflows/release.yml – see README "Releases".
val appVersionName: String = providers.gradleProperty("appVersionName").getOrElse("0.0.0-dev")
val appVersionCode: Int = providers.gradleProperty("appVersionCode").map(String::toInt).getOrElse(1)

val signingEnvNames =
    listOf("SIGNING_KEYSTORE_PATH", "SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD")
val signingEnv: Map<String, String?> = signingEnvNames.associateWith { providers.environmentVariable(it).orNull }
val missingSigningEnv: List<String> = signingEnvNames.filter { signingEnv[it].isNullOrBlank() }

android {
    namespace = "app.brokoli5191.zeitig"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 1
            }
    }

    defaultConfig {
        applicationId = "app.brokoli5191.zeitig"
        minSdk = 28
        targetSdk = 37
        versionName = appVersionName
        versionCode = appVersionCode
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // MapLibre ships native code; 32-bit x86 only matters for old emulators.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        create("release") {
            // Values come only from the environment (GitHub Actions secrets). When they are
            // missing, verifyReleaseSigningEnv fails the release build with a clear message.
            signingEnv["SIGNING_KEYSTORE_PATH"]?.takeIf(String::isNotBlank)?.let { storeFile = file(it) }
            storePassword = signingEnv["SIGNING_STORE_PASSWORD"]
            keyAlias = signingEnv["SIGNING_KEY_ALIAS"]
            keyPassword = signingEnv["SIGNING_KEY_PASSWORD"]
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Sideloaded APKs are downloaded whole: compress MapLibre's native libraries (≈ 36 MB stored
    // uncompressed otherwise) and let the installer extract them.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // The in-app language switch needs every language in every install, also from app bundles.
    bundle {
        language {
            enableSplit = false
        }
    }

    // IzzyOnDroid / F-Droid: no Google-encrypted dependency blob in the artifacts.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

roborazzi {
    // Reference images double as the README screenshots, so they can never drift from the code.
    outputDir.set(rootProject.layout.projectDirectory.dir("docs/screenshots"))
}

val verifyReleaseSigningEnv =
    tasks.register("verifyReleaseSigningEnv") {
        group = "verification"
        description = "Fails the release build loudly when signing secrets are missing."
        val missing = missingSigningEnv
        doLast {
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "Release signing is not configured. Missing environment variables: " +
                        missing.joinToString() + ". Refusing to produce an unsigned release APK.",
                )
            }
        }
    }
tasks.matching { it.name == "validateSigningRelease" || it.name == "packageRelease" }.configureEach {
    dependsOn(verifyReleaseSigningEnv)
}

aboutLibraries {
    // Offline only: licence texts are bundled at build time, nothing is fetched at runtime.
    offlineMode = true
    collect {
        fetchRemoteLicense = false
        fetchRemoteFunding = false
    }
    export {
        excludeFields.addAll("generated")
    }
}

dependencies {
    implementation(project(":data"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.materialkolor)
    implementation(libs.maplibre.android)
    implementation(libs.aboutlibraries.core)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
