package io.github.onprs.universitytoolbox.feature.gpa.impl.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.ui.EmptyStateView
import io.github.onprs.universitytoolbox.feature.gpa.impl.R
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaCalculator
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaCourse
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaScale
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaSummary
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.LetterGrade
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetGpaProjection
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetReachability
import java.util.Locale

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
        onRemoveCourse = viewModel::removeCourse,
        onLoadSampleCourses = { viewModel.loadSampleCourses(sampleNames) },
        onSyncTarget = viewModel::syncTargetFromCurrentCourses,
        onCompletedCreditsChange = viewModel::updateCompletedCreditsInput,
        onCurrentGpaChange = viewModel::updateCurrentGpaInput,
        onPlannedCreditsChange = viewModel::updatePlannedCreditsInput,
        onTargetGpaChange = viewModel::updateTargetGpaInput,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onRemoveCourse: (String) -> Unit,
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
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                Tab(
                    selected = uiState.selectedTab == GpaTab.COURSES,
                    onClick = { onSelectTab(GpaTab.COURSES) },
                    text = { Text(text = stringResource(R.string.feature_gpa_tab_courses)) },
                )
                Tab(
                    selected = uiState.selectedTab == GpaTab.TARGET_PLANNER,
                    onClick = { onSelectTab(GpaTab.TARGET_PLANNER) },
                    text = { Text(text = stringResource(R.string.feature_gpa_tab_target)) },
                )
            }

            ScaleSelectorRow(
                selectedScale = uiState.selectedScale,
                onSelectScale = onSelectScale,
            )

            when (uiState.selectedTab) {
                GpaTab.COURSES -> CoursesTabContent(
                    uiState = uiState,
                    onEditCourseClick = onEditCourseClick,
                    onToggleCourseIncluded = onToggleCourseIncluded,
                    onRemoveCourse = onRemoveCourse,
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

    if (uiState.isCourseDialogOpen) {
        CourseEditorDialog(
            initialCourse = uiState.editingCourse,
            onDismiss = onDismissDialog,
            onSave = onSaveCourse,
        )
    }
}

@Composable
private fun ScaleSelectorRow(selectedScale: GpaScale, onSelectScale: (GpaScale) -> Unit) {
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
private fun CoursesTabContent(
    uiState: GpaUiState,
    onEditCourseClick: (GpaCourse) -> Unit,
    onToggleCourseIncluded: (String) -> Unit,
    onRemoveCourse: (String) -> Unit,
    onLoadSampleCourses: () -> Unit,
) {
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
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = ToolboxSpacing.medium,
                    end = ToolboxSpacing.medium,
                    top = ToolboxSpacing.small,
                    bottom = ToolboxSpacing.sectionGap,
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
                        onRemove = { onRemoveCourse(course.id) },
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

        OutlinedTextField(
            value = uiState.completedCreditsInput,
            onValueChange = onCompletedCreditsChange,
            label = { Text(text = stringResource(R.string.feature_gpa_target_completed_credits)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.currentGpaInput,
            onValueChange = onCurrentGpaChange,
            label = { Text(text = stringResource(R.string.feature_gpa_target_current_gpa)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.plannedCreditsInput,
            onValueChange = onPlannedCreditsChange,
            label = { Text(text = stringResource(R.string.feature_gpa_target_planned_credits)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.targetGpaInput,
            onValueChange = onTargetGpaChange,
            label = { Text(text = stringResource(R.string.feature_gpa_target_goal_gpa)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        uiState.targetProjection?.let { projection ->
            TargetProjectionCard(
                scale = uiState.selectedScale,
                projection = projection,
            )
        }
    }
}

@Composable
private fun TargetProjectionCard(scale: GpaScale, projection: TargetGpaProjection) {
    val containerColor = when (projection.reachability) {
        TargetReachability.ALREADY_SECURED, TargetReachability.ACHIEVABLE ->
            ToolboxTheme.extendedColors.successContainer
        TargetReachability.EXACT_MAX_REQUIRED ->
            ToolboxTheme.extendedColors.warningContainer
        TargetReachability.UNREACHABLE, TargetReachability.INVALID_INPUT ->
            MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (projection.reachability) {
        TargetReachability.ALREADY_SECURED, TargetReachability.ACHIEVABLE ->
            ToolboxTheme.extendedColors.onSuccessContainer
        TargetReachability.EXACT_MAX_REQUIRED ->
            ToolboxTheme.extendedColors.onWarningContainer
        TargetReachability.UNREACHABLE, TargetReachability.INVALID_INPUT ->
            MaterialTheme.colorScheme.onErrorContainer
    }

    val message = when (projection.reachability) {
        TargetReachability.ALREADY_SECURED -> stringResource(R.string.feature_gpa_target_status_secured)
        TargetReachability.ACHIEVABLE -> stringResource(
            R.string.feature_gpa_target_status_achievable,
            formatGpaValue(projection.requiredFutureGpa),
            formatScoreValue(projection.requiredMinimumScore ?: 0.0),
        )
        TargetReachability.EXACT_MAX_REQUIRED -> stringResource(
            R.string.feature_gpa_target_status_exact_max,
            formatGpaValue(scale.maxGpa),
        )
        TargetReachability.UNREACHABLE -> stringResource(
            R.string.feature_gpa_target_status_unreachable,
            formatGpaValue(projection.maxAchievableGpa),
        )
        TargetReachability.INVALID_INPUT -> stringResource(R.string.feature_gpa_target_status_invalid)
    }

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
private fun CourseEditorDialog(
    initialCourse: GpaCourse?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double) -> Unit,
) {
    var name by remember(initialCourse) { mutableStateOf(initialCourse?.name.orEmpty()) }
    var creditsText by remember(initialCourse) {
        mutableStateOf(initialCourse?.credits?.let { formatScoreValue(it) } ?: "3.0")
    }
    var scoreText by remember(initialCourse) {
        mutableStateOf(initialCourse?.score?.let { formatScoreValue(it) } ?: "88")
    }

    val parsedCredits = creditsText.toDoubleOrNull()
    val parsedScore = scoreText.toDoubleOrNull()
    val isValid = name.isNotBlank() &&
        parsedCredits != null &&
        parsedCredits > 0.0 &&
        parsedScore != null &&
        parsedScore in 0.0..100.0

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
            CourseEditorFormContent(
                name = name,
                creditsText = creditsText,
                scoreText = scoreText,
                onNameChange = { name = it },
                onCreditsChange = { creditsText = it },
                onScoreChange = { scoreText = it },
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    if (parsedCredits != null && parsedScore != null) {
                        onSave(name, parsedCredits, parsedScore)
                    }
                },
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
private fun CourseEditorFormContent(
    name: String,
    creditsText: String,
    scoreText: String,
    onNameChange: (String) -> Unit,
    onCreditsChange: (String) -> Unit,
    onScoreChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(text = stringResource(R.string.feature_gpa_course_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = creditsText,
            onValueChange = onCreditsChange,
            label = { Text(text = stringResource(R.string.feature_gpa_course_credits)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = scoreText,
            onValueChange = onScoreChange,
            label = { Text(text = stringResource(R.string.feature_gpa_course_score)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
                    onClick = { onScoreChange(formatScoreValue(grade.representativeScore)) },
                    label = { Text(text = grade.label) },
                )
            }
        }
    }
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
