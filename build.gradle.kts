// Top-level build file. Plugins are declared here once so every module shares one classloader.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.appdistribution) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.herblens.module.rules)
}

// Kotlin/JS tooling pulls in versions of these with advisories (Dependabot); none is in the web bundle.
// Mocha 11, the test runner: serialize-javascript, diff. Firebase's Node build: undici and gRPC (the
// browser build uses fetch and WebChannel). webpack-dev-server and Karma: ws. Drop each as its
// dependent catches up.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().apply {
        resolution("serialize-javascript", "7.0.5")
        resolution("diff", "8.0.3")
        resolution("undici", "6.29.0")
        resolution("@grpc/grpc-js", "1.13.6")
        resolution("ws", "8.21.3")
    }
}
