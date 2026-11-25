package io.github.onprs.universitytoolbox.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Jetpack Compose 约定插件，自动适配 Application 与 Library 模块。
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> {
                    configureAndroidCompose(this)
                }
            }

            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    configureAndroidCompose(this)
                }
            }
        }
    }
}
