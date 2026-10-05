plugins {
    alias(libs.plugins.herblens.kmp.library)
}

// Fakes and fixtures for tests in every module. Only ever added to test source sets.
kotlin {
    js { nodejs() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { nodejs() }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.domain)
            api(libs.kotlin.test)
            api(libs.kotlinx.coroutines.test)
            api(libs.turbine)
        }
        androidMain.dependencies {
            api(libs.junit4)
            api(libs.kotlin.test.junit)
        }
    }
}
