package io.github.onprs.universitytoolbox.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxElevation
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxRadius
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSizes
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import kotlin.math.PI
import kotlin.math.sin

private const val WAVE_ANIMATION_DURATION_MS = 1400
private const val WAVE_CYCLES = 3.5f
private const val WAVE_STEP_PX = 6

/**
 * 确定进度线性指示器封装，统一使用 Material 3 1.4.0 的断点间隙与圆头样式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxLinearProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { clampedProgress },
        modifier = modifier
            .fillMaxWidth()
            .height(ToolboxSizes.progressTrackHeight),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = ToolboxSpacing.extraSmall,
        drawStopIndicator = {
            ProgressIndicatorDefaults.drawStopIndicator(
                drawScope = this,
                stopSize = ProgressIndicatorDefaults.LinearTrackStopIndicatorSize,
                color = color,
                strokeCap = StrokeCap.Round,
            )
        },
    )
}

/**
 * 不定进度线性指示器封装，统一使用 Material 3 1.4.0 的圆角断点规范。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxLinearProgressBar(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .height(ToolboxSizes.progressTrackHeight),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = ToolboxSpacing.extraSmall,
    )
}

/**
 * 波形加载进度条封装：在不确定或流式下载阶段呈现平滑正弦波形动画。
 */
@Composable
fun ToolboxWavyProgressBar(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 4.dp,
) {
    val transition = rememberInfiniteTransition(label = "wavy_progress")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = WAVE_ANIMATION_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavy_phase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp),
    ) {
        val centerY = size.height / 2f
        val strokePx = strokeWidth.toPx()
        val amplitude = (size.height - strokePx) / 2f * 0.65f
        val activeEndX = if (progress == null) {
            size.width
        } else {
            size.width * progress.coerceIn(0f, 1f)
        }

        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )

        if (activeEndX > 0f) {
            val path = Path()
            var x = 0f
            var started = false
            while (x <= activeEndX) {
                val normalizedX = x / size.width.coerceAtLeast(1f)
                val y = centerY + sin(normalizedX * WAVE_CYCLES * (2.0 * PI).toFloat() + phase) * amplitude
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
                x += WAVE_STEP_PX
            }
            if (x - WAVE_STEP_PX < activeEndX) {
                val endNormalized = activeEndX / size.width.coerceAtLeast(1f)
                val endY = centerY + sin(endNormalized * WAVE_CYCLES * (2.0 * PI).toFloat() + phase) * amplitude
                path.lineTo(activeEndX, endY)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * 环形加载指示器封装，统一圆角线帽与默认尺寸。
 */
@Composable
fun ToolboxLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = ToolboxSizes.iconLarge,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 4.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        trackColor = trackColor,
        strokeWidth = strokeWidth,
        strokeCap = StrokeCap.Round,
    )
}

/**
 * 悬浮工具栏容器封装：提供高对比度胶囊浮层用于承载高频快捷操作。
 */
@Composable
fun ToolboxFloatingToolbar(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = ToolboxSpacing.medium,
        vertical = ToolboxSpacing.small,
    ),
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = ToolboxElevation.level2,
        shadowElevation = ToolboxElevation.level3,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * 区块标题栏封装，支持主标题、副标题与右侧操作区。
 */
@Composable
fun ToolboxSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

/**
 * 状态标签胶囊封装。
 */
@Composable
fun ToolboxStatusBadge(text: String, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ToolboxRadius.chip),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = ToolboxSpacing.small,
                vertical = ToolboxSpacing.extraSmall,
            ),
        )
    }
}
