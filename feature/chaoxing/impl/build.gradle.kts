plugins {
    alias(libs.plugins.universitytoolbox.android.feature)
    alias(libs.plugins.universitytoolbox.android.room)
}

android {
    namespace = "io.github.onprs.universitytoolbox.feature.chaoxing.impl"
}

dependencies {
    implementation(libs.universitytoolbox.core.common)
    implementation(libs.universitytoolbox.core.license.api)
    implementation(projects.core.toolApi)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.security)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.scanner)
    implementation(projects.core.update)
    implementation(projects.feature.chaoxing.api)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
