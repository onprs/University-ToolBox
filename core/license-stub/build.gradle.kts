plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.universitytoolbox.android.hilt)
}

android {
    namespace = "io.github.onprs.universitytoolbox.core.license.stub"
}

dependencies {
    implementation(libs.universitytoolbox.core.common)
    implementation(libs.universitytoolbox.core.license.api)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
