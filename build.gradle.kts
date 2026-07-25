// Root build script — plugins are applied per-module; we only register them here.

buildscript {
    // Force-upgrade vulnerable buildscript-only transitives (AGP 8.7.0 internal deps).
    // These do not reach the app runtime classpath; they harden the developer build environment.
    configurations.all {
        resolutionStrategy {
            // Netty: force ALL netty modules to patched version
            force("io.netty:netty-handler:4.1.135.Final")
            force("io.netty:netty-codec-http:4.1.135.Final")
            force("io.netty:netty-codec-http2:4.1.135.Final")
            force("io.netty:netty-handler-proxy:4.1.135.Final")
            force("io.netty:netty-codec-socks:4.1.135.Final")
            force("io.netty:netty-codec:4.1.135.Final")
            force("io.netty:netty-transport:4.1.135.Final")
            force("io.netty:netty-buffer:4.1.135.Final")
            force("io.netty:netty-common:4.1.135.Final")
            force("io.netty:netty-resolver:4.1.135.Final")
            force("io.netty:netty-transport-native-unix-common:4.1.135.Final")
            // Logback
            force("ch.qos.logback:logback-core:1.5.34")
            force("ch.qos.logback:logback-classic:1.5.34")
            // BouncyCastle
            force("org.bouncycastle:bcprov-jdk18on:1.85")
            force("org.bouncycastle:bcpkix-jdk18on:1.85")
            force("org.bouncycastle:bcutil-jdk18on:1.85")
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.benchmark) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
}

allprojects {
    // Enforce explicit API in shared KMP code.
    // Compose UI code is opt-in to internal API; that's intentional.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            // Stable progressive flags. No experimental opt-ins by default.
            // freeCompilerArgs.addAll("-progressive")
        }
    }
}
