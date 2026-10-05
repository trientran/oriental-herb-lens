plugins {
    alias(libs.plugins.herblens.kmp.compose)
}

// A map composable: Mapbox on Android, MapKit on iOS.
kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.mapbox.maps)
        }
    }
}
