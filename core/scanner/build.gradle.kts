plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.universitytoolbox.android.compose)
    alias(libs.plugins.universitytoolbox.android.hilt)
}

android {
    namespace = "io.github.onprs.universitytoolbox.core.scanner"
}

dependencies {
    api(libs.universitytoolbox.core.common)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
