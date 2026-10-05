import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A feature: screens with their ViewModels. Sees only the domain, the design system and common
 * code (see ModuleRulesPlugin); repositories arrive through Koin.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("herblens.kmp.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        fun lib(alias: String) = libs.findLibrary(alias).get()

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                implementation(project(":core:common"))
                implementation(project(":core:domain"))
                implementation(project(":core:designsystem"))
                implementation(project(":core:maps"))
                implementation(lib("jetbrains-lifecycle-viewmodel-compose"))
                implementation(lib("jetbrains-lifecycle-runtime-compose"))
                implementation(lib("jetbrains-navigation-compose"))
                implementation(project.dependencies.platform(lib("koin-bom")))
                implementation(lib("koin-compose-viewmodel"))
                implementation(lib("kotlinx-serialization-json"))
                implementation(lib("kermit"))
            }
            sourceSets.getByName("commonTest").dependencies {
                implementation(project(":core:testing"))
            }
        }
    }
}
