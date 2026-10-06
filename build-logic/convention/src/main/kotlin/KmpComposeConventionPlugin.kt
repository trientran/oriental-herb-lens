import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** A shared module with Compose Multiplatform UI. */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("herblens.kmp.library")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        fun lib(alias: String) = libs.findLibrary(alias).get()

        extensions.configure<KotlinMultiplatformExtension> {
            // Compose resources (strings, fonts) reach the APK as Android assets
            (this as org.gradle.api.plugins.ExtensionAware).extensions
                .configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget>("android") {
                    androidResources { enable = true }
                }
            sourceSets.getByName("commonMain").dependencies {
                implementation(lib("compose-runtime"))
                implementation(lib("compose-foundation"))
                implementation(lib("compose-ui"))
                implementation(lib("compose-material3"))
                implementation(lib("compose-components-resources"))
                implementation(lib("compose-ui-tooling-preview"))
            }
        }
        dependencies { "androidRuntimeClasspath"(lib("compose-ui-tooling")) }

        // Compose tests in the browser need the Skiko runtime bundled by webpack (CMP-4906). These
        // modules' tests are ViewModel tests, which already run on Android and iOS.
        tasks.matching { it.name.startsWith("jsTest") || it.name.startsWith("jsNodeTest") || it.name == "checkComposeUiTestConfigurationForJs" }
            .configureEach { enabled = false }

        // Each module's resources get a Res class in its own package, e.g. com.uri.lee.dl.feature.search.resources
        extensions.configure<ComposeExtension> {
            (this as org.gradle.api.plugins.ExtensionAware).extensions.configure<ResourcesExtension> {
                packageOfResClass = "com.uri.lee.dl" + path.replace(':', '.').replace('-', '_') + ".resources"
            }
        }
    }
}
