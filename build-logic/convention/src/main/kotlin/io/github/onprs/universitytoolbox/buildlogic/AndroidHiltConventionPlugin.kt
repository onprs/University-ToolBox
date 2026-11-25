package io.github.onprs.universitytoolbox.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Hilt (KSP) 依赖注入约定插件，兼容 Android 与纯 JVM 模块。
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")

            dependencies {
                add("ksp", libs.findLibrary("hilt-compiler").get())
            }

            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies {
                    add("implementation", libs.findLibrary("hilt-core").get())
                }
            }

            pluginManager.withPlugin("com.android.base") {
                pluginManager.apply("com.google.dagger.hilt.android")
                dependencies {
                    add("implementation", libs.findLibrary("hilt-android").get())
                }
            }
        }
    }
}
