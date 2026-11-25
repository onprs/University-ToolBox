package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxLinearProgressBar
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxRadius
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.ui.EmptyStateView
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.R
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActivity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActivityStatus
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingUserSignStatus
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingActivitiesUiState
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingActivityFilter
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingActivitiesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaoxingActivitiesScreen(
    viewModel: ChaoxingActivitiesViewModel,
    onOpenSignExecution: (ChaoxingNavKey.SignExecution) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.courseName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.chaoxing_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.chaoxing_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        ActivitiesBody(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onFilterChange = viewModel::setFilter,
            onAutoMonitorToggle = viewModel::toggleAutoMonitor,
            onRefresh = viewModel::refresh,
            onOpenSignExecution = onOpenSignExecution,
        )
    }
}

@Composable
private fun ActivitiesBody(
    state: ChaoxingActivitiesUiState,
    modifier: Modifier,
    onFilterChange: (ChaoxingActivityFilter) -> Unit,
    onAutoMonitorToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenSignExecution: (ChaoxingNavKey.SignExecution) -> Unit,
) {
    val visibleActivities = state.activities.filter { activity ->
        when (state.filter) {
            ChaoxingActivityFilter.ALL -> true
            ChaoxingActivityFilter.SIGN_ONLY -> activity.isSignRelated
            ChaoxingActivityFilter.IN_PROGRESS_ONLY ->
                activity.isSignRelated && activity.activityStatus == ChaoxingActivityStatus.IN_PROGRESS
        }
    }

    Column(modifier = modifier) {
        if (state.isLoading) {
            ToolboxLinearProgressBar()
        }

        ActivitiesControlBar(
            filter = state.filter,
            isAutoMonitorEnabled = state.isAutoMonitorEnabled,
            lastUpdatedAtMs = state.lastUpdatedAtMs,
            errorMessage = state.errorMessage,
            onFilterChange = onFilterChange,
            onAutoMonitorToggle = onAutoMonitorToggle,
        )

        if (visibleActivities.isEmpty()) {
            EmptyStateView(
                title = stringResource(R.string.chaoxing_empty_activities_title),
                description = stringResource(R.string.chaoxing_empty_activities_desc),
                icon = Icons.Outlined.EventAvailable,
                actionLabel = stringResource(R.string.chaoxing_refresh),
                onAction = onRefresh,
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
        ) {
            items(visibleActivities, key = { it.activeId }) { activity ->
                ActivityCard(
                    activity = activity,
                    onClick = {
                        if (activity.isSignRelated) {
                            onOpenSignExecution(
                                ChaoxingNavKey.SignExecution(
                                    accountId = state.accountId,
                                    courseId = state.courseId,
                                    clazzId = state.clazzId,
                                    activeId = activity.activeId,
                                    courseName = state.courseName,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ActivitiesControlBar(
    filter: ChaoxingActivityFilter,
    isAutoMonitorEnabled: Boolean,
    lastUpdatedAtMs: Long?,
    errorMessage: String?,
    onFilterChange: (ChaoxingActivityFilter) -> Unit,
    onAutoMonitorToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ToolboxSpacing.medium, vertical = ToolboxSpacing.small),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = "自动轮询监测", style = MaterialTheme.typography.titleSmall)
                val hint = lastUpdatedAtMs?.let { "上次更新：${formatTimestamp(it)}" } ?: "每 10 秒检查一次进行中签到"
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = isAutoMonitorEnabled, onCheckedChange = onAutoMonitorToggle)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
            item {
                FilterChip(
                    selected = filter == ChaoxingActivityFilter.SIGN_ONLY,
                    onClick = { onFilterChange(ChaoxingActivityFilter.SIGN_ONLY) },
                    label = { Text(text = "仅签到与签退") },
                )
            }
            item {
                FilterChip(
                    selected = filter == ChaoxingActivityFilter.IN_PROGRESS_ONLY,
                    onClick = { onFilterChange(ChaoxingActivityFilter.IN_PROGRESS_ONLY) },
                    label = { Text(text = "仅进行中") },
                )
            }
            item {
                FilterChip(
                    selected = filter == ChaoxingActivityFilter.ALL,
                    onClick = { onFilterChange(ChaoxingActivityFilter.ALL) },
                    label = { Text(text = "全部活动") },
                )
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(ToolboxRadius.chip),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(ToolboxSpacing.small),
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(activity: ChaoxingActivity, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        enabled = activity.isSignRelated,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                val isOngoing = activity.activityStatus == ChaoxingActivityStatus.IN_PROGRESS
                ToolboxStatusBadge(
                    text = activity.activityStatus.displayLabel(),
                    containerColor = if (isOngoing) {
                        ToolboxTheme.extendedColors.successContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (isOngoing) {
                        ToolboxTheme.extendedColors.onSuccessContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolboxStatusBadge(
                    text = activity.signType.displayLabel(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (activity.isSignRelated) {
                    val (badgeBg, badgeFg) = resolveSignStatusColors(activity.userStatus)
                    ToolboxStatusBadge(
                        text = activity.userStatus.displayLabel(),
                        containerColor = badgeBg,
                        contentColor = badgeFg,
                    )
                }
            }

            val meta = listOfNotNull(
                activity.displayTime.takeIf { it.isNotBlank() },
                if (activity.releaseNum > 0) "已参与 ${activity.attendNum}/${activity.releaseNum}" else null,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun resolveSignStatusColors(
    status: ChaoxingUserSignStatus,
): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> = when (status) {
    ChaoxingUserSignStatus.SIGNED,
    ChaoxingUserSignStatus.TEACHER_SIGNED,
    -> ToolboxTheme.extendedColors.successContainer to ToolboxTheme.extendedColors.onSuccessContainer

    ChaoxingUserSignStatus.ABSENT ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

    ChaoxingUserSignStatus.LEAVE,
    ChaoxingUserSignStatus.SICK_LEAVE,
    ChaoxingUserSignStatus.PERSONAL_LEAVE,
    ChaoxingUserSignStatus.PUBLIC_LEAVE,
    -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

    ChaoxingUserSignStatus.UNKNOWN ->
        MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant

    ChaoxingUserSignStatus.UNSIGNED,
    ChaoxingUserSignStatus.LATE,
    ChaoxingUserSignStatus.EARLY_LEAVE,
    ChaoxingUserSignStatus.EXPIRED,
    -> ToolboxTheme.extendedColors.warningContainer to ToolboxTheme.extendedColors.onWarningContainer
}
