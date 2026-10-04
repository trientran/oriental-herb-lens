plugins {
    alias(libs.plugins.herblens.kmp.library)
}

// Preferences DataStore files, one per purpose.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.datastore.preferences.core)
            api(libs.okio)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
