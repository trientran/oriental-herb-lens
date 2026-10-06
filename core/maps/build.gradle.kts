plugins {
    alias(libs.plugins.herblens.kmp.compose)
}

// A map composable: Mapbox on Android, MapKit on iOS, OpenStreetMap tiles drawn by Compose in the browser.
kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.mapbox.maps)
        }
        // OpenStreetMap tiles through the app's image loader
        jsMain.dependencies {
            implementation(libs.coil.compose)
        }
    }
}
