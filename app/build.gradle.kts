import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.sqldelight)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

/** Reads a secret from local.properties, falling back to an environment variable (CI). */
fun secret(name: String): String {
    val value = localProps.getProperty(name) ?: System.getenv(name)
    if (value.isNullOrEmpty()) {
        logger.warn("WARNING: $name is not set in local.properties or the environment; builds will use an empty value.")
        return ""
    }
    return value
}

android {
    namespace = "com.uri.lee.dl"
    compileSdk {
        version = release(37)
    }
    signingConfigs {
        create("release") {
            val storePath = secret("RELEASE_STORE_FILE")
            if (storePath.isNotEmpty()) {
                storeFile = rootProject.file(storePath)
            }
            storePassword = secret("RELEASE_STORE_PASSWORD")
            keyAlias = secret("RELEASE_KEY_ALIAS")
            keyPassword = secret("RELEASE_KEY_PASSWORD")
        }
    }
    defaultConfig {
        applicationId = "com.uri.lee.dl"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "MAP_PRODUCTS_API_KEY", "\"${secret("MAP_PRODUCTS_API_KEY")}\"")
        buildConfigField("String", "IMGE_API_KEY", "\"${secret("IMGE_API_KEY")}\"")
        signingConfig = signingConfigs.getByName("release")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources {
        noCompress += "tflite"
    }
    sourceSets.getByName("main") {
        assets.setSrcDirs(listOf("assets"))
    }
    lint {
        // Issues that predate the migration; new ones still fail the build.
        baseline = file("lint-baseline.xml")
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

sqldelight {
    databases {
        create("HerbLensDatabase") {
            packageName.set("com.uri.lee.dl.data.db")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.places)
    implementation(libs.mapbox.maps)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.glide)
    implementation(libs.glide.okhttp3.integration)
    implementation(libs.subsampling.scale.image.view)
    implementation(libs.timber)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)
    api(libs.guava)

    implementation(libs.mlkit.objectdetection)
    implementation(libs.mlkit.objectdetection.custom)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.image.labeling.custom)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.ui.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.config)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)

    testImplementation(projects.core.testing)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.sqldelight.sqlite.driver)
}
