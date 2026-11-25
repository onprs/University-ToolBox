plugins {
    alias(libs.plugins.universitytoolbox.android.library)
    alias(libs.plugins.universitytoolbox.android.hilt)
    alias(libs.plugins.universitytoolbox.android.room)
}

android {
    namespace = "io.github.onprs.universitytoolbox.core.database"
}

dependencies {
    api(libs.universitytoolbox.core.common)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.turbine)
}
