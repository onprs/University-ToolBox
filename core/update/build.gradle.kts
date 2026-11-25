plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.universitytoolbox.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.onprs.universitytoolbox.core.update"
}

dependencies {
    api(libs.universitytoolbox.core.common)
    api(libs.universitytoolbox.core.license.api)
    implementation(projects.core.network)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.turbine)
}
