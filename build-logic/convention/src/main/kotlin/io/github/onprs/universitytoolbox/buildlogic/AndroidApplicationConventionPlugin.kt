package io.github.onprs.universitytoolbox.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * 宿主应用模块通用构建约定插件。
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig {
                    targetSdk = BuildConstants.TARGET_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            configureStaticAnalysis(isAndroid = true)
        }
    }
}
