plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.universitytoolbox.android.hilt)
}

android {
    namespace = "io.github.onprs.universitytoolbox.core.security"
}

dependencies {
    api(libs.universitytoolbox.core.common)
    api(libs.androidx.datastore)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
