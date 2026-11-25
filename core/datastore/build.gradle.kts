plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.universitytoolbox.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.onprs.universitytoolbox.core.datastore"
}

dependencies {
    api(libs.universitytoolbox.core.common)
    api(libs.universitytoolbox.core.license.api)
    implementation(projects.core.security)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
