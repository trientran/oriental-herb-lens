plugins {
    alias(libs.plugins.herblens.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase through GitLive's multiplatform SDK. GitLive types stay inside this module: if it falls
// behind the official SDKs, only these classes change.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(libs.gitlive.firebase.analytics)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.config)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.bom))
        }
    }
}
