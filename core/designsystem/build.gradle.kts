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

// Android before 14 names Hebrew and Indonesian by their old codes, iw and in, and Compose's resources
// match a language's code as the platform gives it: the resources are built from a copy of
// src/commonMain/composeResources with values-he and values-id also under those names.
val composeResourcesWithOldCodes = layout.buildDirectory.dir("composeResourcesWithOldCodes")
val copyComposeResources = tasks.register<Sync>("copyComposeResourcesWithOldCodes") {
    val source = layout.projectDirectory.dir("src/commonMain/composeResources")
    from(source)
    from(source.dir("values-he")) { into("values-iw") }
    from(source.dir("values-id")) { into("values-in") }
    into(composeResourcesWithOldCodes)
}

compose.resources {
    publicResClass = true
    customDirectory("commonMain", copyComposeResources.map { composeResourcesWithOldCodes.get() })
}
