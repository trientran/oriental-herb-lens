import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Phase 6 spike (temporary): Compose Multiplatform in the browser, as Kotlin/JS and as
// Kotlin/Wasm, to compare bundle size, start-up time and Vietnamese text input. It loads the
// real catalog and searches it with the shared search index. Not part of any app.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    js {
        browser { commonWebpackConfig { outputFileName = "spike.js" } }
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "spike.js" } }
        binaries.executable()
    }
    sourceSets {
        val commonMain by getting {
            // The catalog parser, shared with core:data as is (it's pure Kotlin)
            kotlin.srcDir(rootProject.file("core/data/src/commonMain/kotlin/com/uri/lee/dl/data/catalog"))
            kotlin.exclude("**/CatalogDatabase*", "**/*Repository*", "**/*Source*", "**/BundledCatalog*")
            dependencies {
                implementation(projects.core.common)
                implementation(projects.core.domain)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.material3)
                implementation(libs.compose.components.resources)
            }
        }
    }
}

// Be Vietnam Pro (to check Vietnamese marks render) and the bundled catalog, as Compose resources
val spikeResources by tasks.registering(Sync::class) {
    from(rootProject.file("core/designsystem/src/commonMain/composeResources/font")) { into("font") }
    from(rootProject.file("androidApp/assets/herb_catalog.csv")) { into("files") }
    into(layout.buildDirectory.dir("spikeResources"))
}

compose.resources {
    packageOfResClass = "spike.resources"
    customDirectory("commonMain", spikeResources.map { layout.buildDirectory.dir("spikeResources").get() })
}
