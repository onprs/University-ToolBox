package io.github.onprs.universitytoolbox.feature.gpa.impl.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaCalculator
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaCourse
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaInputValidator
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaScale
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaSummary
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetGpaProjection
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetPlannerValidation
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * GPA 工具页面选项卡枚举。
 */
enum class GpaTab {
    COURSES,
    TARGET_PLANNER,
}

/**
 * GPA 计算器页面状态模型。
 */
data class GpaUiState(
    val selectedTab: GpaTab = GpaTab.COURSES,
    val selectedScale: GpaScale = GpaScale.PKU_4_0,
    val courses: List<GpaCourse> = emptyList(),
    val summary: GpaSummary = GpaCalculator.calculateSummary(emptyList(), GpaScale.PKU_4_0),
    val editingCourse: GpaCourse? = null,
    val isCourseDialogOpen: Boolean = false,
    val pendingDeleteCourse: GpaCourse? = null,
    val isClearAllConfirmOpen: Boolean = false,
    val scrollToCourseId: String? = null,
    val completedCreditsInput: String = "",
    val currentGpaInput: String = "",
    val plannedCreditsInput: String = DEFAULT_PLANNED_CREDITS,
    val targetGpaInput: String = DEFAULT_TARGET_GPA,
    val targetValidation: TargetPlannerValidation = TargetPlannerValidation(),
    val targetProjection: TargetGpaProjection? = null,
) {
    companion object {
        const val DEFAULT_PLANNED_CREDITS = "20"
        const val DEFAULT_TARGET_GPA = "3.7"
    }
}

/**
 * GPA 换算与目标反推业务逻辑控制器。
 */
@HiltViewModel
class GpaViewModel @Inject constructor() : ViewModel() {
    private val mutableUiState = MutableStateFlow(recomputeState(GpaUiState()))
    val uiState: StateFlow<GpaUiState> = mutableUiState.asStateFlow()

    fun selectTab(tab: GpaTab) {
        mutableUiState.update { it.copy(selectedTab = tab) }
    }

    fun selectScale(scale: GpaScale) {
        mutableUiState.update { current ->
            val updatedSummary = GpaCalculator.calculateSummary(current.courses, scale)
            recomputeState(current.copy(selectedScale = scale, summary = updatedSummary))
        }
    }

    fun openAddCourseDialog() {
        mutableUiState.update { it.copy(editingCourse = null, isCourseDialogOpen = true) }
    }

    fun openEditCourseDialog(course: GpaCourse) {
        mutableUiState.update { it.copy(editingCourse = course, isCourseDialogOpen = true) }
    }

    fun dismissCourseDialog() {
        mutableUiState.update { it.copy(editingCourse = null, isCourseDialogOpen = false) }
    }

    fun saveCourse(name: String, credits: Double, score: Double) {
        val trimmedName = name.trim()
        if (!GpaInputValidator.isValidCourseSubmission(trimmedName, credits, score)) {
            return
        }
        mutableUiState.update { current ->
            val editing = current.editingCourse
            var addedCourseId: String? = null
            val nextCourses = if (editing == null) {
                val created = GpaCourse(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
                    credits = credits,
                    score = score,
                    included = true,
                )
                addedCourseId = created.id
                current.courses + created
            } else {
                current.courses.map { item ->
                    if (item.id == editing.id) {
                        item.copy(name = trimmedName, credits = credits, score = score)
                    } else {
                        item
                    }
                }
            }
            val nextSummary = GpaCalculator.calculateSummary(nextCourses, current.selectedScale)
            recomputeState(
                current.copy(
                    courses = nextCourses,
                    summary = nextSummary,
                    editingCourse = null,
                    isCourseDialogOpen = false,
                    scrollToCourseId = addedCourseId,
                ),
            )
        }
    }

    fun consumeScrollTarget() {
        mutableUiState.update { it.copy(scrollToCourseId = null) }
    }

    fun toggleCourseIncluded(courseId: String) {
        mutableUiState.update { current ->
            val nextCourses = current.courses.map { item ->
                if (item.id == courseId) item.copy(included = !item.included) else item
            }
            val nextSummary = GpaCalculator.calculateSummary(nextCourses, current.selectedScale)
            recomputeState(current.copy(courses = nextCourses, summary = nextSummary))
        }
    }

    fun removeCourse(courseId: String) {
        mutableUiState.update { current ->
            val nextCourses = current.courses.filterNot { it.id == courseId }
            val nextSummary = GpaCalculator.calculateSummary(nextCourses, current.selectedScale)
            recomputeState(
                current.copy(
                    courses = nextCourses,
                    summary = nextSummary,
                    pendingDeleteCourse = current.pendingDeleteCourse?.takeUnless { it.id == courseId },
                ),
            )
        }
    }

    fun setPendingDeleteCourse(course: GpaCourse?) {
        mutableUiState.update { it.copy(pendingDeleteCourse = course) }
    }

    fun confirmDeleteCourse() {
        val targetId = mutableUiState.value.pendingDeleteCourse?.id ?: return
        removeCourse(targetId)
    }

    fun setClearAllConfirmOpen(open: Boolean) {
        mutableUiState.update { it.copy(isClearAllConfirmOpen = open && it.courses.isNotEmpty()) }
    }

    fun confirmClearAllCourses() {
        mutableUiState.update { current ->
            val nextSummary = GpaCalculator.calculateSummary(emptyList(), current.selectedScale)
            recomputeState(
                current.copy(
                    courses = emptyList(),
                    summary = nextSummary,
                    pendingDeleteCourse = null,
                    isClearAllConfirmOpen = false,
                    scrollToCourseId = null,
                ),
            )
        }
    }

    fun loadSampleCourses(sampleNames: Triple<String, String, String>) {
        val sampleCourses = listOf(
            GpaCourse(id = "sample-1", name = sampleNames.first, credits = SAMPLE_CREDITS_HIGH, score = SAMPLE_SCORE_A),
            GpaCourse(id = "sample-2", name = sampleNames.second, credits = SAMPLE_CREDITS_LOW, score = SAMPLE_SCORE_B),
            GpaCourse(id = "sample-3", name = sampleNames.third, credits = SAMPLE_CREDITS_MID, score = SAMPLE_SCORE_C),
        )
        mutableUiState.update { current ->
            val nextSummary = GpaCalculator.calculateSummary(sampleCourses, current.selectedScale)
            recomputeState(
                current.copy(
                    courses = sampleCourses,
                    summary = nextSummary,
                    scrollToCourseId = sampleCourses.first().id,
                ),
            )
        }
    }

    fun syncTargetFromCurrentCourses() {
        mutableUiState.update { current ->
            recomputeState(
                current.copy(
                    completedCreditsInput = formatDecimal(current.summary.totalCredits),
                    currentGpaInput = formatDecimal(current.summary.gpa),
                ),
            )
        }
    }

    fun updateCompletedCreditsInput(value: String) {
        updateTargetField { it.copy(completedCreditsInput = value) }
    }

    fun updateCurrentGpaInput(value: String) {
        updateTargetField { it.copy(currentGpaInput = value) }
    }

    fun updatePlannedCreditsInput(value: String) {
        updateTargetField { it.copy(plannedCreditsInput = value) }
    }

    fun updateTargetGpaInput(value: String) {
        updateTargetField { it.copy(targetGpaInput = value) }
    }

    private fun updateTargetField(transform: (GpaUiState) -> GpaUiState) {
        mutableUiState.update { current -> recomputeState(transform(current)) }
    }

    private fun recomputeState(state: GpaUiState): GpaUiState {
        val (validation, projection) = GpaInputValidator.evaluateTargetInputs(
            scale = state.selectedScale,
            completedInput = state.completedCreditsInput,
            currentGpaInput = state.currentGpaInput,
            plannedInput = state.plannedCreditsInput,
            targetGpaInput = state.targetGpaInput,
        )
        return state.copy(
            targetValidation = validation,
            targetProjection = projection,
        )
    }

    private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.2f", value)

    private companion object {
        const val SAMPLE_CREDITS_HIGH = 4.0
        const val SAMPLE_CREDITS_MID = 3.0
        const val SAMPLE_CREDITS_LOW = 2.0
        const val SAMPLE_SCORE_A = 92.0
        const val SAMPLE_SCORE_B = 86.0
        const val SAMPLE_SCORE_C = 89.0
    }
}
