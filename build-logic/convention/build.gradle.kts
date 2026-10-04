plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = libs.plugins.herblens.kmp.library.get().pluginId
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("moduleRules") {
            id = libs.plugins.herblens.module.rules.get().pluginId
            implementationClass = "ModuleRulesPlugin"
        }
    }
}
