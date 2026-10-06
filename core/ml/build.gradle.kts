plugins {
    alias(libs.plugins.herblens.kmp.library)
}

// Herb identification on the device: ML Kit image labelling and object detection (Android; iOS
// through Swift bridges), LiteRT.js in the browser, and decoding picked photos.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        jsMain.dependencies {
            implementation(npm("@litertjs/core", "2.5.3"))
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.mlkit.image.labeling.custom)
            implementation(libs.mlkit.objectdetection)
            implementation(libs.mlkit.image.labeling.play)
            implementation(libs.kermit)
            implementation(libs.androidx.exifinterface)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.koin.android)
        }
    }
}
