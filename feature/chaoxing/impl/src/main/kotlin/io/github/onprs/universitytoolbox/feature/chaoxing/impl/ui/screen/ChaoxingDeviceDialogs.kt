package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxRadius
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScannerView
import io.github.onprs.universitytoolbox.core.ui.PermissionGuideCard
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.ChaoxingAccountShareBundle

private const val INLINE_UNIFIED_ICON_ID = "unified_device_icon"
private const val INLINE_RANDOM_ICON_ID = "random_device_icon"

/**
 * 账号名称旁展示的设备码绑定状态图标按钮（绿色代表已绑定统一设备码，橙色代表使用固定随机设备码）。
 */
@Composable
internal fun DeviceCodeStatusIconButton(isUnified: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = if (isUnified) {
        ToolboxTheme.extendedColors.success
    } else {
        ToolboxTheme.extendedColors.warning
    }
    val icon = if (isUnified) Icons.Outlined.PhonelinkSetup else Icons.Outlined.PhonelinkErase
    val description = if (isUnified) "已绑定统一设备码，点击查看说明" else "使用固定随机设备码，点击查看说明"

    IconButton(
        onClick = onClick,
        modifier = modifier.size(28.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 设备码机制说明与图标显示开关弹窗。
 */
@Composable
internal fun ChaoxingDeviceCodeInfoDialog(
    hideBadge: Boolean,
    onToggleHideBadge: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text(text = "设备码说明") },
        text = {
            DeviceCodeInfoDialogBody(
                hideBadge = hideBadge,
                onToggleHideBadge = onToggleHideBadge,
            )
        },
        confirmButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text(text = "关闭")
            }
        },
    )
}

@Composable
private fun DeviceCodeInfoDialogBody(hideBadge: Boolean, onToggleHideBadge: (Boolean) -> Unit) {
    val inlineIcons = rememberDeviceInlineIcons()
    val thirdBullet = buildAnnotatedString {
        append("• 已绑定统一设备码的账号带有 ")
        appendInlineContent(INLINE_UNIFIED_ICON_ID, "[已绑定]")
        append(" 标识；使用独立固定随机设备码的账号带有 ")
        appendInlineContent(INLINE_RANDOM_ICON_ID, "[随机设备码]")
        append(" 标识。")
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        Text(
            text = "• 为代签账号绑定统一设备码时，请扫描机主在账号管理页生成的账号绑定二维码，或通过导出的绑定链接重新导入。" +
                "绑定统一设备码后，机主在学习通应用、本机及已绑定的代签设备上签到均会被识别为同一台设备，" +
                "避免教师端检测出“更换了签到设备”。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "• 未绑定统一设备码的代签账号会在后续签到中固定各自独立的随机设备码。" +
                "独立随机设备码可避免同机多账号重复冲突，并在仅用当前设备代签时保持设备一致；" +
                "若机主曾在自己的学习通应用或其他设备上签到过，则仍可能出现“更换了签到设备”提示。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = thirdBullet,
            inlineContent = inlineIcons,
            style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = ToolboxSpacing.extraSmall))
        Text(
            text = "前后签到设备检测并非在所有学校启用。若确定所在学校未开启该机制且不想显示设备码图标，" +
                "可开启下方开关关闭提示，后续仍可在账号管理页随时修改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ToolboxRadius.chip))
                .toggleable(
                    value = hideBadge,
                    role = Role.Switch,
                    onValueChange = onToggleHideBadge,
                )
                .padding(vertical = ToolboxSpacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = hideBadge, onCheckedChange = null)
            Text(text = "关闭设备码提示图标", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun rememberDeviceInlineIcons(): Map<String, InlineTextContent> {
    val successColor = ToolboxTheme.extendedColors.success
    val warningColor = ToolboxTheme.extendedColors.warning
    return remember(successColor, warningColor) {
        val placeholder = Placeholder(
            width = 18.sp,
            height = 18.sp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        )
        mapOf(
            INLINE_UNIFIED_ICON_ID to InlineTextContent(placeholder) {
                Icon(
                    imageVector = Icons.Outlined.PhonelinkSetup,
                    contentDescription = null,
                    tint = successColor,
                    modifier = Modifier.size(18.dp),
                )
            },
            INLINE_RANDOM_ICON_ID to InlineTextContent(placeholder) {
                Icon(
                    imageVector = Icons.Outlined.PhonelinkErase,
                    contentDescription = null,
                    tint = warningColor,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}

/**
 * 账号绑定二维码与导入链接分享弹窗。
 */
@Composable
internal fun ChaoxingShareAccountDialog(
    bundle: ChaoxingAccountShareBundle,
    onCopyLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val qrBitmap = remember(bundle.qrPngBytes) {
        BitmapFactory.decodeByteArray(bundle.qrPngBytes, 0, bundle.qrPngBytes.size)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "账号绑定二维码") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
            ) {
                Text(
                    text = "${bundle.displayName}（${bundle.maskedAccount}）",
                    style = MaterialTheme.typography.titleSmall,
                )
                ToolboxStatusBadge(
                    text = if (bundle.isUnifiedDeviceCode) "已包含统一设备码" else "已包含当前固定设备码",
                    containerColor = ToolboxTheme.extendedColors.successContainer,
                    contentColor = ToolboxTheme.extendedColors.onSuccessContainer,
                )
                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(ToolboxRadius.card),
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(vertical = ToolboxSpacing.extraSmall),
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "账号绑定二维码",
                            modifier = Modifier
                                .size(220.dp)
                                .padding(ToolboxSpacing.small),
                        )
                    }
                }
                Text(
                    text = "请让代签同伴在本应用「绑定导入」页扫描上方二维码，或复制下方导入链接发送给对方。链接包含加密登录凭据，请仅分享给信任的同伴。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onCopyLink(bundle.shareUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(text = " 复制导入链接")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "完成")
            }
        },
    )
}

/**
 * 扫描同伴账号绑定二维码的底部抽屉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChaoxingImportQrScannerSheet(onBarcodeScanned: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(hasCameraPermissionGranted(context)) }
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    var hasDeliveredScan by remember { mutableStateOf(false) }

    LifecycleResumeEffect(context) {
        hasCameraPermission = hasCameraPermissionGranted(context)
        onPauseOrDispose {}
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRequestedPermission = true
        hasCameraPermission = granted
    }
    val activity = remember(context) { context.findActivity() }
    val isPermanentlyDenied = isCameraPermissionPermanentlyDenied(
        hasPermission = hasCameraPermission,
        hasRequested = hasRequestedPermission,
        activity = activity,
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
        ) {
            Text(text = "扫描账号绑定二维码", style = MaterialTheme.typography.titleMedium)
            if (hasCameraPermission) {
                BarcodeScannerView(
                    onBarcodeDetected = { result ->
                        val raw = result.rawValue.trim()
                        if (!hasDeliveredScan && raw.isNotEmpty()) {
                            hasDeliveredScan = true
                            onBarcodeScanned(raw)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(ToolboxRadius.card)),
                )
            } else {
                PermissionGuideCard(
                    title = "需要相机权限",
                    rationale = if (isPermanentlyDenied) {
                        "相机权限已被系统关闭，请前往应用设置开启相机权限以扫描账号绑定二维码。"
                    } else {
                        "用于扫描同伴生成的账号绑定二维码并同步统一设备码。"
                    },
                    isPermanentlyDenied = isPermanentlyDenied,
                    onRequestPermission = {
                        if (isPermanentlyDenied) {
                            openAppDetailsSettings(context)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                )
            }
        }
    }
}

private fun hasCameraPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun isCameraPermissionPermanentlyDenied(
    hasPermission: Boolean,
    hasRequested: Boolean,
    activity: Activity?,
): Boolean {
    if (hasPermission || !hasRequested || activity == null) {
        return false
    }
    return !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
