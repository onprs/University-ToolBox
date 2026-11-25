package io.github.onprs.universitytoolbox.feature.home.shortcut

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.onprs.universitytoolbox.feature.home.R
import io.github.onprs.universitytoolbox.feature.home.domain.HomeToolItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 动态应用快捷方式发布契约：根据授权与远程熔断状态注册可用工具快捷方式。
 */
interface ToolShortcutPublisher {
    fun publishShortcuts(tools: List<HomeToolItem>)

    fun reportToolUsed(toolId: String)
}

/**
 * 基于 AndroidX [ShortcutManagerCompat] 的动态 App Shortcuts 发布器。
 */
@Singleton
class AndroidToolShortcutPublisher @Inject constructor(@param:ApplicationContext private val context: Context) :
    ToolShortcutPublisher {
    override fun publishShortcuts(tools: List<HomeToolItem>) {
        runCatching {
            val systemMax = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            val limit = systemMax.coerceIn(1, MAX_PUBLISHED_SHORTCUTS)
            val launchComponent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.component

            val availableOrdered = tools
                .filter { it.isAvailable }
                .sortedWith(
                    compareByDescending<HomeToolItem> { it.isPinned }
                        .thenBy { it.descriptor.sortOrder }
                        .thenBy { it.descriptor.id },
                )
                .take(limit)

            val shortcuts = availableOrdered.mapIndexed { index, item ->
                val tool = item.descriptor
                val shortLabel = runCatching { context.getString(tool.titleRes) }
                    .getOrDefault(tool.id)
                    .ifBlank { tool.id }
                val longLabel = runCatching { context.getString(tool.descriptionRes) }
                    .getOrDefault(shortLabel)
                    .ifBlank { shortLabel }

                val deepLinkIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("$TOOL_URI_PREFIX${tool.id}"),
                ).apply {
                    setPackage(context.packageName)
                    if (launchComponent != null) {
                        component = launchComponent
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                ShortcutInfoCompat.Builder(context, "$SHORTCUT_ID_PREFIX${tool.id}")
                    .setShortLabel(shortLabel)
                    .setLongLabel(longLabel)
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_tool))
                    .setIntent(deepLinkIntent)
                    .setRank(index)
                    .build()
            }

            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    override fun reportToolUsed(toolId: String) {
        runCatching {
            ShortcutManagerCompat.reportShortcutUsed(context, "$SHORTCUT_ID_PREFIX$toolId")
        }
    }

    companion object {
        const val MAX_PUBLISHED_SHORTCUTS = 4
        const val SHORTCUT_ID_PREFIX = "tool_"
        const val TOOL_URI_PREFIX = "universitytoolbox://tool/"
    }
}
