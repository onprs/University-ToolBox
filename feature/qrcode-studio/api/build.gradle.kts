plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.onprs.universitytoolbox.feature.qrcodestudio.api"
}

dependencies {
    api(projects.core.toolApi)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
