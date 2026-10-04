plugins {
    alias(libs.plugins.herblens.kmp.compose)
}

// Theme, components, shared strings and the MVI ViewModel base. Every feature builds on it.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.compose.material3)
            api(libs.compose.components.resources)
            api(libs.compose.material.icons.extended)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
    }
}

compose.resources {
    publicResClass = true
}
