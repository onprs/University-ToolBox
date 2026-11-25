package io.github.onprs.universitytoolbox.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxLoadingIndicator
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxWavyProgressBar
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSizes
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing

/**
 * 通用加载状态视图：支持不确定环形加载及可选的确定进度波形展示。
 */
@Composable
fun LoadingStateView(modifier: Modifier = Modifier, message: String? = null, progress: Float? = null) {
    val resolvedMessage = message ?: stringResource(R.string.core_ui_loading_default)
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(ToolboxSpacing.large),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
        ) {
            if (progress != null) {
                ToolboxWavyProgressBar(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ToolboxLoadingIndicator()
            }
            Text(
                text = resolvedMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 通用空数据占位视图，支持自定义图标、说明文字与引导操作按钮。
 */
@Composable
fun EmptyStateView(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(ToolboxSpacing.large),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 340.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(ToolboxSpacing.medium)
                        .size(ToolboxSizes.iconLarge),
                )
            }
            Spacer(modifier = Modifier.height(ToolboxSpacing.extraSmall))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(modifier = Modifier.height(ToolboxSpacing.small))
                FilledTonalButton(onClick = onAction) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

/**
 * 通用错误状态视图，向用户呈现清晰的错误说明与可选的重试操作。
 */
@Composable
fun ErrorStateView(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val resolvedRetryLabel = retryLabel ?: stringResource(R.string.core_ui_retry_action)
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(ToolboxSpacing.large),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 340.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .padding(ToolboxSpacing.medium)
                        .size(ToolboxSizes.iconLarge),
                )
            }
            Spacer(modifier = Modifier.height(ToolboxSpacing.extraSmall))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(ToolboxSpacing.small))
                Button(onClick = onRetry) {
                    Text(text = resolvedRetryLabel)
                }
            }
        }
    }
}
