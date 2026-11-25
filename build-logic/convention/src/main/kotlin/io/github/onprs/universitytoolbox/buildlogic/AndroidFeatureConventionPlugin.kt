package io.github.onprs.universitytoolbox.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * 业务功能模块通用约定插件，自动装配 Library、Compose、Hilt 与序列化基础设施。
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("universitytoolbox.android.library")
                apply("universitytoolbox.android.compose")
                apply("universitytoolbox.android.hilt")
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            dependencies {
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-navigation3").get())
                add("implementation", libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-navigation3-runtime").get())
                add("implementation", libs.findLibrary("androidx-navigation3-ui").get())
                add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
            }
        }
    }
}
