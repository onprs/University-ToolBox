package io.github.onprs.universitytoolbox.core.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.tool.api.ToolIconKey

/**
 * 将核心工具图标枚举映射为 Material Symbols 矢量图标。
 */
fun ToolIconKey.toImageVector(): ImageVector = when (this) {
    ToolIconKey.ATTENDANCE -> Icons.Outlined.EventAvailable
    ToolIconKey.CALCULATOR -> Icons.Outlined.Calculate
    ToolIconKey.QR_CODE -> Icons.Outlined.QrCode2
    ToolIconKey.SCANNER -> Icons.Outlined.DocumentScanner
    ToolIconKey.UTILITY -> Icons.Outlined.Build
}

/**
 * 获取授权等级对应的本地化名称资源 ID。
 */
@StringRes
fun LicenseTier.labelRes(): Int = when (this) {
    LicenseTier.FREE -> R.string.core_ui_tier_free
    LicenseTier.STANDARD -> R.string.core_ui_tier_standard
    LicenseTier.PRO -> R.string.core_ui_tier_pro
    LicenseTier.FULL -> R.string.core_ui_tier_full
}
