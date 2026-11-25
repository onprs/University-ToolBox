plugins {
    alias(libs.plugins.universitytoolbox.jvm.library)
}

group = "io.github.onprs.universitytoolbox"
version = "0.1.0"

dependencies {
    api(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
