plugins {
    alias(libs.plugins.herblens.kmp.feature)
}

// Identify: the camera (CameraX on Android, AVFoundation on iOS) or picked photos, classified on the device.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.material3.adaptive.layout)
        }
        iosMain.dependencies {
            implementation(projects.core.ml)
        }
        androidMain.dependencies {
            // Camera frames go straight to ML Kit (an allowed exception in the module rules)
            implementation(projects.core.ml)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.activity.compose)
        }
    }
}
