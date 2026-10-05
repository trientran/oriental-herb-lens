plugins {
    alias(libs.plugins.herblens.kmp.library)
}

// Herb identification on the device: ML Kit image labelling and object detection, and decoding
// picked photos. Android here; iOS (Swift) in Phase 5.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.mlkit.image.labeling.custom)
            implementation(libs.mlkit.objectdetection)
            implementation(libs.androidx.exifinterface)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.koin.android)
        }
    }
}
