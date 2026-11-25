package io.github.onprs.universitytoolbox.feature.settings.domain

import io.github.onprs.universitytoolbox.core.common.security.Redactor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 诊断日志脱敏格式化工具，生成可直接展示与分享导出的纯文本报告。
 */
object DiagnosticLogFormatter {
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 将诊断审计记录格式化为脱敏文本；所有摘要与操作描述均经过 [Redactor.redactText] 二次过滤。
     */
    fun formatReport(
        appVersionName: String,
        appVersionCode: Long,
        licenseTierLabel: String,
        isOfficialEngine: Boolean,
        logs: List<AuditLogRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        appendLine("University-ToolBox Diagnostic Report")
        appendLine("Version: $appVersionName ($appVersionCode)")
        appendLine("License Tier: $licenseTierLabel")
        appendLine("Engine Mode: ${if (isOfficialEngine) "OFFICIAL" else "DEV_STUB"}")
        appendLine("Entries: ${logs.size}")
        appendLine("---")
        if (logs.isEmpty()) {
            appendLine("(No diagnostic audit entries)")
        } else {
            logs.forEach { entry ->
                val formattedTime = timeFormatter.format(
                    Instant.ofEpochMilli(entry.createdAtEpochMs).atZone(zoneId),
                )
                val safeCategory = Redactor.redactText(entry.category)
                val safeAction = Redactor.redactText(entry.action)
                val safeSummary = Redactor.redactText(entry.summary)
                appendLine("[$formattedTime] [$safeCategory/$safeAction] $safeSummary")
            }
        }
    }
}
