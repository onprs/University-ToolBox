plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.universitytoolbox.android.hilt)
}

android {
    namespace = "io.github.onprs.universitytoolbox.core.license.stub"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.licenseApi)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
