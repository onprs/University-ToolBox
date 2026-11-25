package io.github.onprs.universitytoolbox.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSizes
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing

/**
 * 系统权限引导卡片：清晰说明所需权限用途并提供授权或跳转设置入口。
 */
@Composable
fun PermissionGuideCard(
    title: String,
    rationale: String,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    isPermanentlyDenied: Boolean = false,
    icon: ImageVector = Icons.Outlined.VerifiedUser,
) {
    val resolvedActionLabel = actionLabel ?: if (isPermanentlyDenied) {
        stringResource(R.string.core_ui_open_settings)
    } else {
        stringResource(R.string.core_ui_grant_permission)
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ToolboxSizes.iconMedium),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onRequestPermission,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = resolvedActionLabel)
            }
        }
    }
}
