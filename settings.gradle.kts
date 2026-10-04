pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Tools the Kotlin/JS and Wasm test runners download (Node.js, Yarn, Binaryen)
        ivy("https://nodejs.org/dist") {
            name = "Node.js"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen"
            patternLayout { artifact("version_[revision]/[module]-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
        maven("https://api.mapbox.com/downloads/v2/releases/maven") {
            authentication { create<BasicAuthentication>("basic") }
            credentials {
                // Do not change the username below. It should always be `mapbox` (not your username).
                username = "mapbox"
                // The secret token lives in ~/.gradle/gradle.properties
                password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull ?: ""
            }
        }
    }
}

rootProject.name = "oriental-herb-lens"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":androidApp")
include(":composeApp")
include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:firebase")
include(":core:location")
include(":core:ml")
include(":core:network")
include(":core:testing")
