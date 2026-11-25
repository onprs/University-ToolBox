plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.onprs.universitytoolbox.feature.chaoxing.api"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
