package io.github.onprs.universitytoolbox.feature.settings.domain

/**
 * 关键运行时权限类型。
 */
enum class PermissionKind {
    CAMERA,
    LOCATION,
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
