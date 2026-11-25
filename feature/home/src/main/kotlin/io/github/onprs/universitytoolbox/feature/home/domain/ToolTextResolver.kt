package io.github.onprs.universitytoolbox.feature.home.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 解析工具描述符对应本地化标题与描述的抽象，便于纯 JVM 单元测试隔离 Android Context。
 */
interface ToolTextResolver {
    fun getTitle(tool: ToolDescriptor): String

    fun getDescription(tool: ToolDescriptor): String
}

/**
 * 基于 Android [Context] 的默认本地化工具文案解析器。
 */
@Singleton
class AndroidToolTextResolver @Inject constructor(@param:ApplicationContext private val context: Context) :
    ToolTextResolver {
    override fun getTitle(tool: ToolDescriptor): String = runCatching {
        context.getString(tool.titleRes)
    }.getOrDefault(tool.id)

    override fun getDescription(tool: ToolDescriptor): String = runCatching {
        context.getString(tool.descriptionRes)
    }.getOrDefault("")
}
