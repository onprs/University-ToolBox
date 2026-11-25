import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

group = "io.github.onprs.universitytoolbox"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ktlint {
    android.set(false)
    ignoreFailures.set(false)
    filter {
        exclude { element ->
            val path = element.file.path.replace('\\', '/')
            path.contains("/build/") || path.contains("/generated/")
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(file("../../config/detekt/detekt.yml"))
    parallel = true
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
    }
    exclude("**/build/**", "**/generated/**")
}

dependencies {
    api(project(":common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
