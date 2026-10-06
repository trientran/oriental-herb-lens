plugins {
    alias(libs.plugins.herblens.kmp.library)
}

// Preference files, one per purpose: DataStore on Android and iOS, localStorage in the browser.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        getByName("mobileMain").dependencies {
            api(libs.androidx.datastore.preferences.core)
            api(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
