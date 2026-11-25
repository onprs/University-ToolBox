package io.github.onprs.universitytoolbox.feature.gpa.impl.ui

import android.view.WindowManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.ui.EmptyStateView
import io.github.onprs.universitytoolbox.feature.gpa.impl.R
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.CourseFieldError
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.CourseFormValidation
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaCalculator
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaCourse
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaInputValidator
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaScale
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaSummary
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.LetterGrade
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetFieldError
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetGpaProjection
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetPlannerValidation
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetReachability
import java.util.Locale

private val FAB_SAFE_BOTTOM_PADDING = 88.dp

/**
 * GPA 计算器功能路由入口。
 */
@Composable
fun GpaRoute(modifier: Modifier = Modifier, viewModel: GpaViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val sampleNames = Triple(
        stringResource(R.string.feature_gpa_sample_course_1),
        stringResource(R.string.feature_gpa_sample_course_2),
        stringResource(R.string.feature_gpa_sample_course_3),
    )

    GpaScreen(
        uiState = uiState,
        onBackClick = { backDispatcher?.onBackPressed() },
        onSelectTab = viewModel::selectTab,
        onSelectScale = viewModel::selectScale,
        onAddCourseClick = viewModel::openAddCourseDialog,
        onEditCourseClick = viewModel::openEditCourseDialog,
        onDismissDialog = viewModel::dismissCourseDialog,
        onSaveCourse = viewModel::saveCourse,
        onToggleCourseIncluded = viewModel::toggleCourseIncluded,
        onRequestDeleteCourse = viewModel::setPendingDeleteCourse,
        onConfirmDeleteCourse = viewModel::confirmDeleteCourse,
        onSetClearAllConfirmOpen = viewModel::setClearAllConfirmOpen,
        onConfirmClearAll = viewModel::confirmClearAllCourses,
        onScrollConsumed = viewModel::consumeScrollTarget,
        onLoadSampleCourses = { viewModel.loadSampleCourses(sampleNames) },
        onSyncTarget = viewModel::syncTargetFromCurrentCourses,
        onCompletedCreditsChange = viewModel::updateCompletedCreditsInput,
        onCurrentGpaChange = viewModel::updateCurrentGpaInput,
        onPlannedCreditsChange = viewModel::updatePlannedCreditsInput,
        onTargetGpaChange = viewModel::updateTargetGpaInput,
        modifier = modifier,
    )
}

@Composable
internal fun GpaScreen(
    uiState: GpaUiState,
    onBackClick: () -> Unit,
    onSelectTab: (GpaTab) -> Unit,
    onSelectScale: (GpaScale) -> Unit,
    onAddCourseClick: () -> Unit,
    onEditCourseClick: (GpaCourse) -> Unit,
    onDismissDialog: () -> Unit,
    onSaveCourse: (String, Double, Double) -> Unit,
    onToggleCourseIncluded: (String) -> Unit,
    onRequestDeleteCourse: (GpaCourse?) -> Unit,
    onConfirmDeleteCourse: () -> Unit,
    onSetClearAllConfirmOpen: (Boolean) -> Unit,
    onConfirmClearAll: () -> Unit,
    onScrollConsumed: () -> Unit,
    onLoadSampleCourses: () -> Unit,
    onSyncTarget: () -> Unit,
    onCompletedCreditsChange: (String) -> Unit,
    onCurrentGpaChange: (String) -> Unit,
    onPlannedCreditsChange: (String) -> Unit,
    onTargetGpaChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            GpaTopBar(
                showClearAction = uiState.selectedTab == GpaTab.COURSES && uiState.courses.isNotEmpty(),
                onBackClick = onBackClick,
                onClearAllClick = { onSetClearAllConfirmOpen(true) },
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == GpaTab.COURSES) {
                FloatingActionButton(onClick = onAddCourseClick) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.feature_gpa_add_course),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            GpaTabsAndScaleHeader(
                selectedTab = uiState.selectedTab,
                selectedScale = uiState.selectedScale,
                onSelectTab = onSelectTab,
                onSelectScale = onSelectScale,
            )

            when (uiState.selectedTab) {
                GpaTab.COURSES -> CoursesTabContent(
                    uiState = uiState,
                    onEditCourseClick = onEditCourseClick,
                    onToggleCourseIncluded = onToggleCourseIncluded,
                    onRequestDeleteCourse = onRequestDeleteCourse,
                    onScrollConsumed = onScrollConsumed,
                    onLoadSampleCourses = onLoadSampleCourses,
                )
                GpaTab.TARGET_PLANNER -> TargetPlannerTabContent(
                    uiState = uiState,
                    onSyncTarget = onSyncTarget,
                    onCompletedCreditsChange = onCompletedCreditsChange,
                    onCurrentGpaChange = onCurrentGpaChange,
                    onPlannedCreditsChange = onPlannedCreditsChange,
                    onTargetGpaChange = onTargetGpaChange,
                )
            }
        }
    }

    GpaScreenDialogs(
        uiState = uiState,
        onDismissCourseDialog = onDismissDialog,
        onSaveCourse = onSaveCourse,
        onDismissDeleteConfirm = { onRequestDeleteCourse(null) },
        onConfirmDeleteCourse = onConfirmDeleteCourse,
        onDismissClearAllConfirm = { onSetClearAllConfirmOpen(false) },
        onConfirmClearAll = onConfirmClearAll,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GpaTopBar(showClearAction: Boolean, onBackClick: () -> Unit, onClearAllClick: () -> Unit) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.feature_gpa_screen_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.feature_gpa_back),
                )
            }
        },
        actions = {
            if (showClearAction) {
                IconButton(onClick = onClearAllClick) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(R.string.feature_gpa_clear_all),
                    )
                }
            }
        },
    )
}

@Composable
private fun GpaTabsAndScaleHeader(
    selectedTab: GpaTab,
    selectedScale: GpaScale,
    onSelectTab: (GpaTab) -> Unit,
    onSelectScale: (GpaScale) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
        Tab(
            selected = selectedTab == GpaTab.COURSES,
            onClick = { onSelectTab(GpaTab.COURSES) },
            text = { Text(text = stringResource(R.string.feature_gpa_tab_courses)) },
        )
        Tab(
            selected = selectedTab == GpaTab.TARGET_PLANNER,
            onClick = { onSelectTab(GpaTab.TARGET_PLANNER) },
            text = { Text(text = stringResource(R.string.feature_gpa_tab_target)) },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ToolboxSpacing.medium, vertical = ToolboxSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        GpaScale.entries.forEach { scale ->
            FilterChip(
                selected = scale == selectedScale,
                onClick = { onSelectScale(scale) },
                label = { Text(text = scaleLabel(scale)) },
            )
        }
    }
}

@Composable
private fun GpaScreenDialogs(
    uiState: GpaUiState,
    onDismissCourseDialog: () -> Unit,
    onSaveCourse: (String, Double, Double) -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    onConfirmDeleteCourse: () -> Unit,
    onDismissClearAllConfirm: () -> Unit,
    onConfirmClearAll: () -> Unit,
) {
    if (uiState.isCourseDialogOpen) {
        CourseEditorDialog(
            initialCourse = uiState.editingCourse,
            onDismiss = onDismissCourseDialog,
            onSave = onSaveCourse,
        )
    }

    uiState.pendingDeleteCourse?.let { target ->
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirm,
            title = { Text(text = stringResource(R.string.feature_gpa_delete_confirm_title)) },
            text = { Text(text = stringResource(R.string.feature_gpa_delete_confirm_message, target.name)) },
            confirmButton = {
                TextButton(onClick = onConfirmDeleteCourse) {
                    Text(
                        text = stringResource(R.string.feature_gpa_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteConfirm) {
                    Text(text = stringResource(R.string.feature_gpa_cancel))
                }
            },
        )
    }

    if (uiState.isClearAllConfirmOpen) {
        AlertDialog(
            onDismissRequest = onDismissClearAllConfirm,
            title = { Text(text = stringResource(R.string.feature_gpa_clear_confirm_title)) },
            text = { Text(text = stringResource(R.string.feature_gpa_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmClearAll) {
                    Text(
                        text = stringResource(R.string.feature_gpa_confirm_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClearAllConfirm) {
                    Text(text = stringResource(R.string.feature_gpa_cancel))
                }
            },
        )
    }
}

@Composable
private fun CoursesTabContent(
    uiState: GpaUiState,
    onEditCourseClick: (GpaCourse) -> Unit,
    onToggleCourseIncluded: (String) -> Unit,
    onRequestDeleteCourse: (GpaCourse) -> Unit,
    onScrollConsumed: () -> Unit,
    onLoadSampleCourses: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.scrollToCourseId, uiState.courses.size) {
        val targetId = uiState.scrollToCourseId ?: return@LaunchedEffect
        val targetIndex = uiState.courses.indexOfFirst { it.id == targetId }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
        onScrollConsumed()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GpaSummaryCard(
            summary = uiState.summary,
            modifier = Modifier.padding(horizontal = ToolboxSpacing.medium),
        )

        if (uiState.courses.isEmpty()) {
            EmptyStateView(
                title = stringResource(R.string.feature_gpa_empty_title),
                description = stringResource(R.string.feature_gpa_empty_desc),
                icon = Icons.Outlined.Calculate,
                actionLabel = stringResource(R.string.feature_gpa_load_sample),
                onAction = onLoadSampleCourses,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = ToolboxSpacing.medium,
                    end = ToolboxSpacing.medium,
                    top = ToolboxSpacing.small,
                    bottom = FAB_SAFE_BOTTOM_PADDING,
                ),
                verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
            ) {
                items(items = uiState.courses, key = { it.id }) { course ->
                    val point = GpaCalculator.pointForScore(course.score, uiState.selectedScale)
                    CourseRowCard(
                        course = course,
                        gradePoint = point,
                        onClick = { onEditCourseClick(course) },
                        onToggleIncluded = { onToggleCourseIncluded(course.id) },
                        onRemove = { onRequestDeleteCourse(course) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GpaSummaryCard(summary: GpaSummary, modifier: Modifier = Modifier) {
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
                Column {
                    Text(
                        text = stringResource(R.string.feature_gpa_summary_gpa),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatGpaValue(summary.gpa),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ToolboxStatusBadge(
                    text = scaleLabel(summary.scale),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricColumn(
                    label = stringResource(R.string.feature_gpa_summary_weighted),
                    value = formatScoreValue(summary.weightedAverageScore),
                )
                MetricColumn(
                    label = stringResource(R.string.feature_gpa_summary_arithmetic),
                    value = formatScoreValue(summary.arithmeticAverageScore),
                )
            }
            Text(
                text = stringResource(
                    R.string.feature_gpa_summary_credits,
                    formatScoreValue(summary.totalCredits),
                    summary.includedCourseCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CourseRowCard(
    course: GpaCourse,
    gradePoint: Double,
    onClick: () -> Unit,
    onToggleIncluded: () -> Unit,
    onRemove: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolboxSpacing.small, vertical = ToolboxSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = course.included,
                onCheckedChange = { onToggleIncluded() },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = ToolboxSpacing.small),
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (course.included) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = stringResource(
                        R.string.feature_gpa_course_meta,
                        formatScoreValue(course.credits),
                        formatScoreValue(course.score),
                        formatGpaValue(gradePoint),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.feature_gpa_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TargetPlannerTabContent(
    uiState: GpaUiState,
    onSyncTarget: () -> Unit,
    onCompletedCreditsChange: (String) -> Unit,
    onCurrentGpaChange: (String) -> Unit,
    onPlannedCreditsChange: (String) -> Unit,
    onTargetGpaChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val validation = uiState.targetValidation
    val scale = uiState.selectedScale

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ToolboxSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
    ) {
        if (uiState.summary.totalCredits > 0.0) {
            OutlinedButton(
                onClick = onSyncTarget,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = null,
                    modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
                )
                Text(text = stringResource(R.string.feature_gpa_target_sync))
            }
        }

        TargetDecimalField(
            value = uiState.completedCreditsInput,
            onValueChange = onCompletedCreditsChange,
            label = stringResource(R.string.feature_gpa_target_completed_credits),
            errorMessage = targetFieldErrorText(validation.completedCreditsError, scale),
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )
        TargetDecimalField(
            value = uiState.currentGpaInput,
            onValueChange = onCurrentGpaChange,
            label = stringResource(R.string.feature_gpa_target_current_gpa),
            errorMessage = targetFieldErrorText(validation.currentGpaError, scale),
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )
        TargetDecimalField(
            value = uiState.plannedCreditsInput,
            onValueChange = onPlannedCreditsChange,
            label = stringResource(R.string.feature_gpa_target_planned_credits),
            errorMessage = targetFieldErrorText(validation.plannedCreditsError, scale),
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )
        TargetDecimalField(
            value = uiState.targetGpaInput,
            onValueChange = onTargetGpaChange,
            label = stringResource(R.string.feature_gpa_target_goal_gpa),
            errorMessage = targetFieldErrorText(validation.targetGpaError, scale),
            imeAction = ImeAction.Done,
            onImeAction = {
                focusManager.clearFocus()
                keyboardController?.hide()
            },
        )

        TargetPlannerResultSection(
            scale = scale,
            validation = validation,
            projection = uiState.targetProjection,
        )
    }
}

@Composable
private fun TargetDecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String?,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        isError = errorMessage != null,
        supportingText = errorMessage?.let { text -> { Text(text = text) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() },
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TargetPlannerResultSection(
    scale: GpaScale,
    validation: TargetPlannerValidation,
    projection: TargetGpaProjection?,
) {
    if (projection != null) {
        TargetProjectionCard(
            scale = scale,
            projection = projection,
            validation = validation,
        )
    } else if (validation.isAwaitingBaseInput) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.feature_gpa_target_guide_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(ToolboxSpacing.medium),
            )
        }
    }
}

@Composable
private fun TargetProjectionCard(
    scale: GpaScale,
    projection: TargetGpaProjection,
    validation: TargetPlannerValidation,
) {
    val isZeroPlannedMet = validation.zeroPlannedCurrentGpa != null && validation.zeroPlannedTargetMet
    val containerColor = when {
        isZeroPlannedMet -> ToolboxTheme.extendedColors.successContainer
        projection.reachability == TargetReachability.ALREADY_SECURED ||
            projection.reachability == TargetReachability.ACHIEVABLE ->
            ToolboxTheme.extendedColors.successContainer
        projection.reachability == TargetReachability.EXACT_MAX_REQUIRED ->
            ToolboxTheme.extendedColors.warningContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when {
        isZeroPlannedMet -> ToolboxTheme.extendedColors.onSuccessContainer
        projection.reachability == TargetReachability.ALREADY_SECURED ||
            projection.reachability == TargetReachability.ACHIEVABLE ->
            ToolboxTheme.extendedColors.onSuccessContainer
        projection.reachability == TargetReachability.EXACT_MAX_REQUIRED ->
            ToolboxTheme.extendedColors.onWarningContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    val message = resolveTargetStatusMessage(scale, projection, validation)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            ToolboxStatusBadge(
                text = scaleLabel(scale),
                containerColor = containerColor,
                contentColor = contentColor,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (projection.reachability != TargetReachability.INVALID_INPUT) {
                Text(
                    text = stringResource(
                        R.string.feature_gpa_target_max_possible,
                        formatGpaValue(projection.maxAchievableGpa),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun resolveTargetStatusMessage(
    scale: GpaScale,
    projection: TargetGpaProjection,
    validation: TargetPlannerValidation,
): String = when {
    validation.zeroPlannedCurrentGpa != null && validation.zeroPlannedTargetMet -> stringResource(
        R.string.feature_gpa_target_status_zero_planned_met,
        formatGpaValue(validation.zeroPlannedCurrentGpa),
    )
    validation.zeroPlannedCurrentGpa != null -> stringResource(
        R.string.feature_gpa_target_status_zero_planned_unmet,
        formatGpaValue(validation.zeroPlannedCurrentGpa),
    )
    projection.reachability == TargetReachability.ALREADY_SECURED ->
        stringResource(R.string.feature_gpa_target_status_secured)
    projection.reachability == TargetReachability.ACHIEVABLE -> stringResource(
        R.string.feature_gpa_target_status_achievable,
        formatGpaValue(projection.requiredFutureGpa),
        formatScoreValue(projection.requiredMinimumScore ?: 0.0),
    )
    projection.reachability == TargetReachability.EXACT_MAX_REQUIRED -> stringResource(
        R.string.feature_gpa_target_status_exact_max,
        formatGpaValue(scale.maxGpa),
    )
    projection.reachability == TargetReachability.UNREACHABLE && validation.targetExceedsScaleMax ->
        stringResource(
            R.string.feature_gpa_target_status_exceeds_scale,
            formatGpaValue(scale.maxGpa),
            formatGpaValue(projection.maxAchievableGpa),
        )
    projection.reachability == TargetReachability.UNREACHABLE -> stringResource(
        R.string.feature_gpa_target_status_unreachable,
        formatGpaValue(projection.maxAchievableGpa),
    )
    else -> stringResource(R.string.feature_gpa_target_status_invalid)
}

@Composable
private fun targetFieldErrorText(error: TargetFieldError?, scale: GpaScale): String? = when (error) {
    TargetFieldError.INVALID_NUMBER -> stringResource(R.string.feature_gpa_error_number_invalid)
    TargetFieldError.NEGATIVE_CREDITS -> stringResource(R.string.feature_gpa_error_credits_negative)
    TargetFieldError.CREDITS_TOO_LARGE -> stringResource(
        R.string.feature_gpa_error_total_credits_max,
        formatScoreValue(GpaInputValidator.MAX_TOTAL_CREDITS),
    )
    TargetFieldError.ZERO_PLANNED_CREDITS -> stringResource(R.string.feature_gpa_error_planned_zero)
    TargetFieldError.GPA_OUT_OF_SCALE_RANGE -> stringResource(
        R.string.feature_gpa_error_gpa_range,
        formatGpaValue(scale.maxGpa),
    )
    null -> null
}

@Composable
private fun CourseEditorDialog(
    initialCourse: GpaCourse?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double) -> Unit,
) {
    val courseKey = initialCourse?.id ?: "new_course"
    var name by rememberSaveable(courseKey) { mutableStateOf(initialCourse?.name.orEmpty()) }
    var creditsText by rememberSaveable(courseKey) {
        mutableStateOf(initialCourse?.credits?.let(::formatScoreValue) ?: "3.0")
    }
    var scoreText by rememberSaveable(courseKey) {
        mutableStateOf(initialCourse?.score?.let(::formatScoreValue) ?: "88")
    }
    var nameInteracted by rememberSaveable(courseKey) { mutableStateOf(initialCourse != null) }

    val validation = GpaInputValidator.validateCourseForm(
        name = name,
        creditsInput = creditsText,
        scoreInput = scoreText,
        showBlankNameError = nameInteracted,
    )

    val submitIfValid = {
        val credits = validation.parsedCredits
        val score = validation.parsedScore
        if (validation.isValid && credits != null && score != null) {
            onSave(name, credits, score)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (initialCourse == null) {
                        R.string.feature_gpa_add_course
                    } else {
                        R.string.feature_gpa_edit_course
                    },
                ),
            )
        },
        text = {
            AdjustResizeDialogWindowEffect()
            CourseEditorFormContent(
                name = name,
                creditsText = creditsText,
                scoreText = scoreText,
                validation = validation,
                onNameChange = {
                    name = it
                    nameInteracted = true
                },
                onCreditsChange = { creditsText = it },
                onScoreChange = { scoreText = it },
                onSubmit = submitIfValid,
            )
        },
        confirmButton = {
            TextButton(
                enabled = validation.isValid,
                onClick = submitIfValid,
            ) {
                Text(text = stringResource(R.string.feature_gpa_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.feature_gpa_cancel))
            }
        },
    )
}

@Composable
private fun AdjustResizeDialogWindowEffect() {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        @Suppress("DEPRECATION")
        dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}

@Composable
private fun CourseEditorFormContent(
    name: String,
    creditsText: String,
    scoreText: String,
    validation: CourseFormValidation,
    onNameChange: (String) -> Unit,
    onCreditsChange: (String) -> Unit,
    onScoreChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameError = courseFieldErrorText(validation.nameError)
    val creditsError = courseFieldErrorText(validation.creditsError)
    val scoreError = courseFieldErrorText(validation.scoreError)

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(text = stringResource(R.string.feature_gpa_course_name)) },
            isError = nameError != null,
            supportingText = nameError?.let { text -> { Text(text = text) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = creditsText,
            onValueChange = onCreditsChange,
            label = { Text(text = stringResource(R.string.feature_gpa_course_credits)) },
            isError = creditsError != null,
            supportingText = creditsError?.let { text -> { Text(text = text) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = scoreText,
            onValueChange = onScoreChange,
            label = { Text(text = stringResource(R.string.feature_gpa_course_score)) },
            isError = scoreError != null,
            supportingText = scoreError?.let { text -> { Text(text = text) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSubmit()
                },
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        QuickGradeChipsSection(onSelectScore = { onScoreChange(formatScoreValue(it)) })
    }
}

@Composable
private fun QuickGradeChipsSection(onSelectScore: (Double) -> Unit) {
    Spacer(modifier = Modifier.height(ToolboxSpacing.extraSmall))
    Text(
        text = stringResource(R.string.feature_gpa_quick_grade),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
    ) {
        LetterGrade.entries.forEach { grade ->
            SuggestionChip(
                onClick = { onSelectScore(grade.representativeScore) },
                label = { Text(text = grade.label) },
            )
        }
    }
}

@Composable
private fun courseFieldErrorText(error: CourseFieldError?): String? = when (error) {
    CourseFieldError.NAME_BLANK -> stringResource(R.string.feature_gpa_error_name_blank)
    CourseFieldError.INVALID_NUMBER -> stringResource(R.string.feature_gpa_error_number_invalid)
    CourseFieldError.CREDITS_OUT_OF_RANGE -> stringResource(
        R.string.feature_gpa_error_credits_range,
        formatScoreValue(GpaInputValidator.MAX_COURSE_CREDITS),
    )
    CourseFieldError.SCORE_OUT_OF_RANGE -> stringResource(R.string.feature_gpa_error_score_range)
    null -> null
}

@Composable
private fun scaleLabel(scale: GpaScale): String = when (scale) {
    GpaScale.PKU_4_0 -> stringResource(R.string.feature_gpa_scale_pku)
    GpaScale.STANDARD_4_0 -> stringResource(R.string.feature_gpa_scale_standard_40)
    GpaScale.WES_4_0 -> stringResource(R.string.feature_gpa_scale_wes_40)
    GpaScale.STANDARD_4_3 -> stringResource(R.string.feature_gpa_scale_standard_43)
    GpaScale.FIVE_POINT_5_0 -> stringResource(R.string.feature_gpa_scale_five_point)
}

private fun formatGpaValue(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun formatScoreValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
