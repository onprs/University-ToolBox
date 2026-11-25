package io.github.onprs.universitytoolbox.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSizes
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.license.api.AccessDenialReason
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.license.api.LockReason

/**
 * 受限工具状态卡片：向用户展示工具需要的授权等级或临时维护状态，并引导激活或升级。
 */
@Composable
fun LockedToolCard(
    toolTitle: String,
    toolDescription: String,
    requiredTier: LicenseTier,
    modifier: Modifier = Modifier,
    denialReason: AccessDenialReason? = null,
    isRemoteDisabled: Boolean = false,
    actionLabel: String? = null,
    onUnlockClick: (() -> Unit)? = null,
) {
    val tierLabel = stringResource(requiredTier.labelRes())
    val statusText = resolveLockedReasonText(
        denialReason = denialReason,
        isRemoteDisabled = isRemoteDisabled,
    )
    val badgeColors = if (isRemoteDisabled || denialReason is AccessDenialReason.RemotelyDisabled) {
        ToolboxTheme.extendedColors.warningContainer to ToolboxTheme.extendedColors.onWarningContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isRemoteDisabled || denialReason is AccessDenialReason.RemotelyDisabled) {
                            Icons.Outlined.Build
                        } else {
                            Icons.Outlined.Lock
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(ToolboxSizes.iconMedium),
                    )
                    Text(
                        text = toolTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                ToolboxStatusBadge(
                    text = tierLabel,
                    containerColor = badgeColors.first,
                    contentColor = badgeColors.second,
                )
            }

            if (toolDescription.isNotBlank()) {
                Text(
                    text = toolDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            if (!isRemoteDisabled && denialReason !is AccessDenialReason.RemotelyDisabled && onUnlockClick != null) {
                Button(
                    onClick = onUnlockClick,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = actionLabel ?: stringResource(R.string.core_ui_unlock_action))
                }
            }
        }
    }
}

@Composable
private fun resolveLockedReasonText(denialReason: AccessDenialReason?, isRemoteDisabled: Boolean): String {
    if (isRemoteDisabled) {
        return stringResource(R.string.core_ui_locked_remotely_disabled)
    }
    return when (denialReason) {
        AccessDenialReason.RemotelyDisabled -> stringResource(R.string.core_ui_locked_remotely_disabled)
        AccessDenialReason.EngineInitializing -> stringResource(R.string.core_ui_locked_initializing)
        AccessDenialReason.Expired -> stringResource(R.string.core_ui_locked_expired)
        is AccessDenialReason.InsufficientTier -> stringResource(
            R.string.core_ui_locked_insufficient_tier,
            stringResource(denialReason.required.labelRes()),
            stringResource(denialReason.current.labelRes()),
        )
        is AccessDenialReason.MissingScope -> stringResource(R.string.core_ui_locked_missing_scope)
        is AccessDenialReason.LicenseLocked -> lockReasonText(denialReason.reason)
        null -> stringResource(R.string.core_ui_locked_not_activated)
    }
}

@Composable
private fun lockReasonText(reason: LockReason): String = when (reason) {
    LockReason.NOT_ACTIVATED -> stringResource(R.string.core_ui_locked_not_activated)
    LockReason.EXPIRED -> stringResource(R.string.core_ui_locked_expired)
    LockReason.CLOCK_ROLLBACK_DETECTED -> stringResource(R.string.core_ui_locked_clock_rollback)
    LockReason.REVOKED -> stringResource(R.string.core_ui_locked_revoked)
    LockReason.INVALID_SIGNATURE -> stringResource(R.string.core_ui_locked_invalid_signature)
}
