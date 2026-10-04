plugins {
    alias(libs.plugins.herblens.kmp.library)
}

// Device location services: reverse geocoding (Android Geocoder, iOS CLGeocoder).
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
