plugins {
    alias(libs.plugins.herblens.kmp.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// The shared app: App() with navigation, and the Koin graph for Android and iOS.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.domain)
            api(projects.core.designsystem)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.network)
            implementation(projects.core.firebase)
            api(projects.core.ml)
            api(projects.core.location)
            api(projects.feature.auth)
            api(projects.feature.browse)
            api(projects.feature.contribute)
            api(projects.feature.herbdetails)
            api(projects.feature.identify)
            api(projects.feature.profile)
            api(projects.feature.saved)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.androidx.navigationevent.compose)
            implementation(libs.compose.material3.adaptive.navigation.suite)
            implementation(libs.compose.material3.adaptive.layout)
            implementation(libs.compose.material3.adaptive.navigation)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.koin.test)
        }
        // Screenshot tests: Robolectric renders each screen on the JVM, Roborazzi compares it
        // with the PNGs in src/androidHostTest/screenshots. Record new ones with
        // ./gradlew :composeApp:testAndroidHostTest -Proborazzi.test.record=true
        getByName("androidHostTest").dependencies {
            implementation(libs.junit4)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

roborazzi {
    outputDir.set(file("src/androidHostTest/screenshots"))
}

// Robolectric reaches into JDK internals that recent JDKs keep closed by default
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
}
