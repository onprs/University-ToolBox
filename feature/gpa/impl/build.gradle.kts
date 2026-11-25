plugins {
    alias(libs.plugins.universitytoolbox.android.feature)
}

android {
    namespace = "io.github.onprs.universitytoolbox.feature.gpa.impl"
}

dependencies {
    implementation(projects.feature.gpa.api)
    implementation(libs.universitytoolbox.core.common)
    implementation(libs.universitytoolbox.core.license.api)
    implementation(projects.core.toolApi)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
