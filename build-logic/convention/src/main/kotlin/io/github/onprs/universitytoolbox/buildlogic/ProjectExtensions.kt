package io.github.onprs.universitytoolbox.buildlogic

import com.android.build.api.dsl.CommonExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

internal object BuildConstants {
    const val COMPILE_SDK = 36
    const val MIN_SDK = 26
    const val TARGET_SDK = 36
    val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_17
    val JVM_TARGET: JvmTarget = JvmTarget.JVM_17
    const val JVM_TARGET_STRING = "17"
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = BuildConstants.COMPILE_SDK

        defaultConfig {
            minSdk = BuildConstants.MIN_SDK
        }

        compileOptions {
            sourceCompatibility = BuildConstants.JAVA_VERSION
            targetCompatibility = BuildConstants.JAVA_VERSION
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions.configureSharedKotlinOptions()
    }
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = BuildConstants.JAVA_VERSION
        targetCompatibility = BuildConstants.JAVA_VERSION
    }

    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions.configureSharedKotlinOptions()
    }
}

private fun KotlinJvmCompilerOptions.configureSharedKotlinOptions() {
    jvmTarget.set(BuildConstants.JVM_TARGET)
}

internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        buildFeatures {
            compose = true
        }
    }

    dependencies {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}

internal fun Project.configureStaticAnalysis(isAndroid: Boolean) {
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    extensions.configure<KtlintExtension> {
        android.set(isAndroid)
        ignoreFailures.set(false)
        filter {
            exclude { element ->
                val path = element.file.path.replace('\\', '/')
                path.contains("/build/") || path.contains("/generated/")
            }
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
        parallel = true
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = BuildConstants.JVM_TARGET_STRING
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
        }
        exclude("**/build/**", "**/generated/**")
    }
}
