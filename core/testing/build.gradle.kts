plugins {
    alias(libs.plugins.herblens.jvm.library)
}

dependencies {
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
