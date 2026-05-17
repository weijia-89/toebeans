// :androidApp — Android-only Compose application
//
// Vibe-dangerous surfaces per AGENTS.md:
//   - src/main/kotlin/app/toebeans/android/notifications/
//   - src/main/AndroidManifest.xml (permission allowlist enforced by fitness function)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "app.toebeans.android"
    compileSdk =
        libs.versions.android.compile.sdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "app.toebeans.android"
        minSdk =
            libs.versions.android.min.sdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.target.sdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        // BuildConfig is used by StaleEventGuard to gate crash-on-debug vs log-on-release
        // behavior for stale-event rendering. AGENTS.md treats Gradle dep adds as
        // vibe-dangerous; enabling buildConfig is a config-feature flag (no new dep on
        // the classpath, no runtime library, just an AGP-emitted BuildConfig class).
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":shared"))

    // Compose
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.uiTooling)
    implementation(compose.preview)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    // material-icons-extended is ~5 MB on disk but R8 drops unused icons in release builds.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    // kotlinx-datetime is `api`-exposed by :shared/commonMain but the KMP-to-Android
    // metadata pipeline does not re-export it as compile classpath for :androidApp.
    // Adding it directly mirrors what would happen anyway and avoids fragile transitive
    // resolution. Same version pin as :shared per libs.versions.toml.
    implementation(libs.kotlinx.datetime)

    // DI
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.androidx.compose)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

ktlint {
    version.set("1.3.1")
    android.set(true)
    ignoreFailures.set(false)
}

tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { fte -> fte.file.invariantSeparatorsPath.contains("/build/generated/") }
}
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask>().configureEach {
    exclude { fte -> fte.file.invariantSeparatorsPath.contains("/build/generated/") }
}

detekt {
    config.setFrom(files("$rootDir/.detekt.yml"))
    buildUponDefaultConfig = true
}
