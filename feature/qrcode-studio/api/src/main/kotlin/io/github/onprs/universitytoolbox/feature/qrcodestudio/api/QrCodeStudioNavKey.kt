package io.github.onprs.universitytoolbox.feature.qrcodestudio.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 条码/二维码检视工作台工具注册唯一标识。 */
const val QR_CODE_STUDIO_TOOL_ID = "qrcode_studio"

/**
 * 条码/二维码扫描检视工作台的 Navigation 3 路由键。
 */
@Serializable
data object QrCodeStudioNavKey : NavKey
