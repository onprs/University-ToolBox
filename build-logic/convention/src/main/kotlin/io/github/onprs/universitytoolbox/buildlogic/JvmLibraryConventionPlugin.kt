package io.github.onprs.universitytoolbox.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 纯 Kotlin JVM 库模块构建约定插件。
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureKotlinJvm()
            configureStaticAnalysis(isAndroid = false)
        }
    }
}
