package io.github.onprs.universitytoolbox.core.tool.api

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * Navigation 3 模块化路由安装器契约，供各功能模块配合 Hilt `@IntoSet` 注册页面路由。
 */
typealias EntryProviderInstaller = EntryProviderScope<NavKey>.() -> Unit

/**
 * 将多个模块化路由安装器批量装配到当前 [EntryProviderScope]。
 */
fun EntryProviderScope<NavKey>.installAll(installers: Iterable<EntryProviderInstaller>) {
    installers.forEach { installer -> installer() }
}
