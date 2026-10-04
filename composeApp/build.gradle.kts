plugins {
    alias(libs.plugins.herblens.kmp.library)
}

// The shared app: wires every module into one Koin graph for Android and iOS. Compose screens
// (App(), navigation) join it in Phase 4.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.network)
            implementation(projects.core.firebase)
            api(projects.core.ml)
            api(projects.core.location)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.koin.test)
        }
    }
}
