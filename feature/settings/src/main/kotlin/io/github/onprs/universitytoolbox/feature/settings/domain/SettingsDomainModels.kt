package io.github.onprs.universitytoolbox.feature.settings.domain

/**
 * 主题显示模式选项。
 */
enum class ThemeModeOption {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 应用内界面语言选项。
 */
enum class LanguageOption(val localeTag: String) {
    SYSTEM(""),
    ZH_CN("zh-CN"),
    EN("en"),
    ;

    companion object {
        fun fromTag(tag: String): LanguageOption = entries.firstOrNull {
            it.localeTag.equals(tag, ignoreCase = true)
        } ?: when {
            tag.startsWith("zh", ignoreCase = true) -> ZH_CN
            tag.startsWith("en", ignoreCase = true) -> EN
            else -> SYSTEM
        }
    }
}

/**
 * 关键运行时权限类型。
 */
enum class PermissionKind {
    CAMERA,
    NOTIFICATIONS,
    INSTALL_UNKNOWN_APPS,
}

/**
 * 单项系统权限当前核验状态。
 */
data class PermissionStatusItem(
    val kind: PermissionKind,
    val isGranted: Boolean,
    val requiresSystemSettings: Boolean = false,
)

/**
 * 供诊断导出使用的脱敏审计日志纯模型。
 */
data class AuditLogRecord(
    val id: Long,
    val createdAtEpochMs: Long,
    val category: String,
    val action: String,
    val summary: String,
)
