plugins {
    alias(libs.plugins.herblens.kmp.feature)
}

// User-trained models (plan Phase 7): collect or import photos, train, use and share models.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.training)
        }
    }
}
