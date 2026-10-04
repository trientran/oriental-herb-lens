plugins {
    alias(libs.plugins.herblens.kmp.library)
}

// Herb identification: ML Kit custom image labelling. Android here; iOS (Swift) in Phase 5.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.mlkit.image.labeling.custom)
            implementation(libs.kotlinx.coroutines.play.services)
        }
    }
}
