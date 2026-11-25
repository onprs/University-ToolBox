plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.universitytoolbox.android.compose)
}

android {
    namespace = "io.github.onprs.universitytoolbox.core.tool.api"
}

dependencies {
    api(libs.universitytoolbox.core.license.api)
    api(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
}
