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

include(":app")
include(":core:testing")
