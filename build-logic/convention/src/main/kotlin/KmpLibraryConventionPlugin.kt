import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A shared module: Android, iOS (device and Apple-silicon simulator) and the web (Kotlin/JS, plan
 * D16). The namespace comes from the module path, so `:core:domain` is `com.uri.lee.dl.core.domain`.
 *
 * Code for Android and iOS but not the browser (SQLite, DataStore files, ML Kit) goes in
 * `mobileMain`. Pure modules also build for Wasm, to keep them free of JS-only APIs.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    /** The SQLite catalog: the web keeps the catalog in memory instead. */
    private val mobileOnly = setOf(":core:database")

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<KotlinMultiplatformExtension> {
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                namespace = "com.uri.lee.dl" + path.replace(':', '.').replace('-', '_')
                compileSdk = 37
                minSdk = 26
                // Android resources in host tests: Robolectric screenshots, Compose resources
                withHostTest { isIncludeAndroidResources = true }
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
            iosArm64()
            iosSimulatorArm64()
            if (path !in mobileOnly) {
                // Tests run on Node; the browser is for the app
                js {
                    browser { testTask { enabled = false } }
                    nodejs()
                }
            }
            applyDefaultHierarchyTemplate {
                common {
                    group("mobile") {
                        withCompilations { it.target.name == "android" }
                        group("ios") { withIos() }
                    }
                }
            }

            compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.findLibrary("kotlin-test").get())
            }
        }
    }
}
