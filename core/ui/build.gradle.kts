plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.universitytoolbox.android.compose)
}

android {
    namespace = "io.github.onprs.universitytoolbox.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(libs.universitytoolbox.core.license.api)
    api(projects.core.toolApi)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
