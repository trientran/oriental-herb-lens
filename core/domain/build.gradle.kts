plugins {
    alias(libs.plugins.herblens.kmp.library)
}

// Entities, repository interfaces and use cases. Pure Kotlin: see the module rules in build-logic.
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { nodejs() }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
