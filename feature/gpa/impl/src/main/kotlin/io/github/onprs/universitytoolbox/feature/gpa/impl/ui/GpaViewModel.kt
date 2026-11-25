package io.github.onprs.universitytoolbox.feature.gpa.impl.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaCalculator
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaCourse
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaScale
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaSummary
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetGpaProjection
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetGpaRequest
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
    val completedCreditsInput: String = "",
    val currentGpaInput: String = "",
    val plannedCreditsInput: String = DEFAULT_PLANNED_CREDITS,
    val targetGpaInput: String = DEFAULT_TARGET_GPA,
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
    private val mutableUiState = MutableStateFlow(GpaUiState())
    val uiState: StateFlow<GpaUiState> = mutableUiState.asStateFlow()

    fun selectTab(tab: GpaTab) {
        mutableUiState.update { it.copy(selectedTab = tab) }
    }

    fun selectScale(scale: GpaScale) {
        mutableUiState.update { current ->
            val updatedSummary = GpaCalculator.calculateSummary(current.courses, scale)
            val updated = current.copy(
                selectedScale = scale,
                summary = updatedSummary,
            )
            updated.copy(targetProjection = recomputeTargetProjection(updated))
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
        if (!isValidCourseInput(trimmedName, credits, score)) {
            return
        }
        val normalizedScore = score.coerceIn(0.0, MAX_PERCENT_SCORE)
        mutableUiState.update { current ->
            val editing = current.editingCourse
            val nextCourses = if (editing == null) {
                current.courses + GpaCourse(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
                    credits = credits,
                    score = normalizedScore,
                    included = true,
                )
            } else {
                current.courses.map { item ->
                    if (item.id == editing.id) {
                        item.copy(name = trimmedName, credits = credits, score = normalizedScore)
                    } else {
                        item
                    }
                }
            }
            val nextSummary = GpaCalculator.calculateSummary(nextCourses, current.selectedScale)
            val updated = current.copy(
                courses = nextCourses,
                summary = nextSummary,
                editingCourse = null,
                isCourseDialogOpen = false,
            )
            updated.copy(targetProjection = recomputeTargetProjection(updated))
        }
    }

    fun toggleCourseIncluded(courseId: String) {
        mutableUiState.update { current ->
            val nextCourses = current.courses.map { item ->
                if (item.id == courseId) item.copy(included = !item.included) else item
            }
            val nextSummary = GpaCalculator.calculateSummary(nextCourses, current.selectedScale)
            val updated = current.copy(courses = nextCourses, summary = nextSummary)
            updated.copy(targetProjection = recomputeTargetProjection(updated))
        }
    }

    fun removeCourse(courseId: String) {
        mutableUiState.update { current ->
            val nextCourses = current.courses.filterNot { it.id == courseId }
            val nextSummary = GpaCalculator.calculateSummary(nextCourses, current.selectedScale)
            val updated = current.copy(courses = nextCourses, summary = nextSummary)
            updated.copy(targetProjection = recomputeTargetProjection(updated))
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
            val updated = current.copy(courses = sampleCourses, summary = nextSummary)
            updated.copy(targetProjection = recomputeTargetProjection(updated))
        }
    }

    fun syncTargetFromCurrentCourses() {
        mutableUiState.update { current ->
            val updated = current.copy(
                completedCreditsInput = formatDecimal(current.summary.totalCredits),
                currentGpaInput = formatDecimal(current.summary.gpa),
            )
            updated.copy(targetProjection = recomputeTargetProjection(updated))
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
        mutableUiState.update { current ->
            val updated = transform(current)
            updated.copy(targetProjection = recomputeTargetProjection(updated))
        }
    }

    private fun recomputeTargetProjection(state: GpaUiState): TargetGpaProjection? {
        val completed = state.completedCreditsInput.toDoubleOrNull() ?: return null
        val currentGpa = state.currentGpaInput.toDoubleOrNull() ?: return null
        val planned = state.plannedCreditsInput.toDoubleOrNull() ?: return null
        val target = state.targetGpaInput.toDoubleOrNull() ?: return null
        return GpaCalculator.projectTargetGpa(
            TargetGpaRequest(
                scale = state.selectedScale,
                completedCredits = completed,
                currentGpa = currentGpa,
                plannedCredits = planned,
                targetGpa = target,
            ),
        )
    }

    private fun isValidCourseInput(name: String, credits: Double, score: Double): Boolean =
        name.isNotEmpty() && credits.isFinite() && credits > 0.0 && score.isFinite()

    private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.2f", value)

    private companion object {
        const val MAX_PERCENT_SCORE = 100.0
        const val SAMPLE_CREDITS_HIGH = 4.0
        const val SAMPLE_CREDITS_MID = 3.0
        const val SAMPLE_CREDITS_LOW = 2.0
        const val SAMPLE_SCORE_A = 92.0
        const val SAMPLE_SCORE_B = 86.0
        const val SAMPLE_SCORE_C = 89.0
    }
}
