plugins {
    alias(libs.plugins.herblens.kmp.compose)
}

// A map composable: Mapbox on Android, MapKit on iOS, Leaflet with OpenStreetMap in the browser.
kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.mapbox.maps)
        }
        jsMain.dependencies {
            implementation(npm("leaflet", "1.9.4"))
        }
    }
}
