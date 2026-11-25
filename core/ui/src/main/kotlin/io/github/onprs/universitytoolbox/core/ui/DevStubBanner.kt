package io.github.onprs.universitytoolbox.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.license.api.LicenseState

private const val STUB_CHALLENGE_PREFIX = "DEV-STUB-"

/**
 * 仅在非正式授权引擎模式下展示简洁的 `DEV STUB` 状态指示芯片。
 */
@Composable
fun DevStubBanner(isOfficialEngine: Boolean, modifier: Modifier = Modifier) {
    if (!shouldShowDevStubBanner(isOfficialEngine)) {
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ToolboxSpacing.medium, vertical = ToolboxSpacing.extraSmall),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            shape = CircleShape,
            color = ToolboxTheme.extendedColors.warningContainer,
            contentColor = ToolboxTheme.extendedColors.onWarningContainer,
        ) {
            Text(
                text = stringResource(R.string.core_ui_dev_stub_badge),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(
                    horizontal = ToolboxSpacing.small,
                    vertical = ToolboxSpacing.extraSmall,
                ),
            )
        }
    }
}

/**
 * 根据 [LicenseState] 自动判断是否需要展示 `DEV STUB` 状态指示芯片。
 */
@Composable
fun DevStubBanner(licenseState: LicenseState, modifier: Modifier = Modifier) {
    DevStubBanner(
        isOfficialEngine = !isStubLicenseState(licenseState),
        modifier = modifier,
    )
}

internal fun shouldShowDevStubBanner(isOfficialEngine: Boolean): Boolean = !isOfficialEngine

internal fun isStubLicenseState(licenseState: LicenseState): Boolean = when (licenseState) {
    LicenseState.Initializing -> false
    is LicenseState.Unlocked -> !licenseState.entitlement.isOfficialEngine
    is LicenseState.Locked -> licenseState.deviceChallenge.startsWith(STUB_CHALLENGE_PREFIX)
}
