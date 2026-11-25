plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.universitytoolbox.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.onprs.universitytoolbox.core.network"
}

dependencies {
    api(libs.universitytoolbox.core.common)
    api(libs.universitytoolbox.core.license.api)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
