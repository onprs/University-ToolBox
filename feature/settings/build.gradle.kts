plugins {
    alias(libs.plugins.universitytoolbox.android.feature)
}

android {
    namespace = "io.github.onprs.universitytoolbox.feature.settings"
}

dependencies {
    implementation(libs.universitytoolbox.core.common)
    implementation(libs.universitytoolbox.core.license.api)
    implementation(projects.core.toolApi)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.datastore)
    implementation(projects.core.database)
    implementation(projects.core.update)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
