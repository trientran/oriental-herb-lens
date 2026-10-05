plugins {
    alias(libs.plugins.herblens.kmp.library)
    alias(libs.plugins.sqldelight)
}

// The on-device database: the species catalog and the user's favourites and history.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.coroutines)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

sqldelight {
    databases {
        create("HerbLensDatabase") {
            packageName.set("com.uri.lee.dl.data.db")
        }
    }
}
