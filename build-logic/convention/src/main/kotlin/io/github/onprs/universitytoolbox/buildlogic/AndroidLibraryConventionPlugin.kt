package io.github.onprs.universitytoolbox.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Android 库模块通用构建约定插件。
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    val consumerRules = file("consumer-rules.pro")
                    if (consumerRules.exists()) {
                        consumerProguardFiles(consumerRules)
                    }
                }
                testOptions {
                    targetSdk = BuildConstants.TARGET_SDK
                    unitTests.isIncludeAndroidResources = true
                }
                lint {
                    targetSdk = BuildConstants.TARGET_SDK
                }
            }

            configureStaticAnalysis(isAndroid = true)
        }
    }
}
