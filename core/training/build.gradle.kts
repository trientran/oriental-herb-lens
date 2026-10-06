plugins {
    alias(libs.plugins.herblens.kmp.library)
}

// User-trained models (plan Phase 7): training a classifier's final layer on image embeddings.
// Pure Kotlin, so the same trainer runs on Android, iOS and the web.
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { nodejs() }
    sourceSets {
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
