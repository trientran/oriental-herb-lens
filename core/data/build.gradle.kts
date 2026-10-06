plugins {
    alias(libs.plugins.herblens.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Repository implementations, content sync and mappers. Everything here is reached through the
// domain's interfaces; only dataModule is meant to be used from outside.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.datastore)
            implementation(projects.core.network)
            implementation(projects.core.firebase)
            implementation(projects.core.ml)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        getByName("mobileMain").dependencies {
            implementation(projects.core.database)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        getByName("androidHostTest").dependencies {
            implementation(projects.core.testing)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.ktor.client.mock)
            implementation(libs.okio.fakefilesystem)
        }
    }
}

// Firebase's iOS frameworks are linked by the iOS app, not here, so iOS test binaries can't link.
// The shared logic is tested on the JVM; iOS still compiles in CI.
tasks.matching { it.name == "iosSimulatorArm64Test" || it.name.startsWith("linkDebugTestIos") }.configureEach { enabled = false }
