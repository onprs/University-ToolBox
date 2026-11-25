package io.github.onprs.universitytoolbox.feature.gpa.impl.ui

import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.CourseFieldError
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaInputValidator
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaScale
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetFieldError
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetReachability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpaViewModelTest {
    @Test
    fun `adding toggling and switching scales updates summary and target projection`() {
        val viewModel = GpaViewModel()
        viewModel.openAddCourseDialog()
        assertTrue(viewModel.uiState.value.isCourseDialogOpen)

        viewModel.saveCourse(name = "Algorithms", credits = 4.0, score = 90.0)
        assertFalse(viewModel.uiState.value.isCourseDialogOpen)
        assertEquals(1, viewModel.uiState.value.courses.size)
        assertEquals(viewModel.uiState.value.courses.single().id, viewModel.uiState.value.scrollToCourseId)

        viewModel.consumeScrollTarget()
        assertNull(viewModel.uiState.value.scrollToCourseId)

        viewModel.saveCourse(name = "Physics", credits = 2.0, score = 80.0)
        assertEquals(2, viewModel.uiState.value.courses.size)
        assertEquals(viewModel.uiState.value.courses.last().id, viewModel.uiState.value.scrollToCourseId)

        viewModel.selectScale(GpaScale.STANDARD_4_0)
        // (4*4.0 + 2*3.0) / 6 = 3.6667
        assertEquals(22.0 / 6.0, viewModel.uiState.value.summary.gpa, 1e-4)

        val physicsId = viewModel.uiState.value.courses[1].id
        viewModel.toggleCourseIncluded(physicsId)
        assertEquals(4.0, viewModel.uiState.value.summary.gpa, 1e-4)

        viewModel.syncTargetFromCurrentCourses()
        viewModel.updatePlannedCreditsInput("4")
        viewModel.updateTargetGpaInput("3.5")
        val projection = viewModel.uiState.value.targetProjection
        assertNotNull(projection)
        assertEquals(TargetReachability.ACHIEVABLE, projection?.reachability)

        viewModel.removeCourse(physicsId)
        assertEquals(1, viewModel.uiState.value.courses.size)
    }

    @Test
    fun `edit course updates existing record instead of appending duplicate`() {
        val viewModel = GpaViewModel()
        viewModel.saveCourse("Math", 3.0, 75.0)
        val created = viewModel.uiState.value.courses.single()
        viewModel.consumeScrollTarget()

        viewModel.openEditCourseDialog(created)
        viewModel.saveCourse("Advanced Math", 4.0, 95.0)

        assertEquals(1, viewModel.uiState.value.courses.size)
        assertNull(viewModel.uiState.value.scrollToCourseId)
        val edited = viewModel.uiState.value.courses.single()
        assertEquals(created.id, edited.id)
        assertEquals("Advanced Math", edited.name)
        assertEquals(4.0, edited.credits, 1e-4)
        assertEquals(95.0, edited.score, 1e-4)
    }

    @Test
    fun `delete and clear confirmation flows guard against accidental removal`() {
        val viewModel = GpaViewModel()
        viewModel.loadSampleCourses(Triple("高等数学", "大学英语", "数据结构"))
        assertEquals(3, viewModel.uiState.value.courses.size)

        val firstCourse = viewModel.uiState.value.courses.first()
        viewModel.setPendingDeleteCourse(firstCourse)
        assertEquals(firstCourse, viewModel.uiState.value.pendingDeleteCourse)

        viewModel.setPendingDeleteCourse(null)
        assertNull(viewModel.uiState.value.pendingDeleteCourse)
        assertEquals(3, viewModel.uiState.value.courses.size)

        viewModel.setPendingDeleteCourse(firstCourse)
        viewModel.confirmDeleteCourse()
        assertNull(viewModel.uiState.value.pendingDeleteCourse)
        assertEquals(2, viewModel.uiState.value.courses.size)

        viewModel.setClearAllConfirmOpen(true)
        assertTrue(viewModel.uiState.value.isClearAllConfirmOpen)
        viewModel.setClearAllConfirmOpen(false)
        assertFalse(viewModel.uiState.value.isClearAllConfirmOpen)
        assertEquals(2, viewModel.uiState.value.courses.size)

        viewModel.setClearAllConfirmOpen(true)
        viewModel.confirmClearAllCourses()
        assertFalse(viewModel.uiState.value.isClearAllConfirmOpen)
        assertTrue(viewModel.uiState.value.courses.isEmpty())
    }

    @Test
    fun `validator supports comma decimals and flags out of range or zero planned inputs`() {
        val validForm = GpaInputValidator.validateCourseForm(
            name = "线性代数",
            creditsInput = "3,5",
            scoreInput = "91,5",
            showBlankNameError = true,
        )
        assertTrue(validForm.isValid)
        assertEquals(3.5, validForm.parsedCredits!!, 1e-4)
        assertEquals(91.5, validForm.parsedScore!!, 1e-4)

        val invalidForm = GpaInputValidator.validateCourseForm(
            name = " ",
            creditsInput = "-2",
            scoreInput = "120",
            showBlankNameError = true,
        )
        assertFalse(invalidForm.isValid)
        assertEquals(CourseFieldError.NAME_BLANK, invalidForm.nameError)
        assertEquals(CourseFieldError.CREDITS_OUT_OF_RANGE, invalidForm.creditsError)
        assertEquals(CourseFieldError.SCORE_OUT_OF_RANGE, invalidForm.scoreError)

        val viewModel = GpaViewModel()
        viewModel.saveCourse("非法成绩", credits = 3.0, score = 150.0)
        viewModel.saveCourse("非法学分", credits = -1.0, score = 85.0)
        assertTrue(viewModel.uiState.value.courses.isEmpty())

        viewModel.updateCompletedCreditsInput("60,0")
        viewModel.updateCurrentGpaInput("3,6")
        viewModel.updatePlannedCreditsInput("0")
        viewModel.updateTargetGpaInput("3,5")
        val zeroPlannedValidation = viewModel.uiState.value.targetValidation
        assertEquals(TargetFieldError.ZERO_PLANNED_CREDITS, zeroPlannedValidation.plannedCreditsError)
        assertEquals(3.6, zeroPlannedValidation.zeroPlannedCurrentGpa!!, 1e-4)
        assertTrue(zeroPlannedValidation.zeroPlannedTargetMet)

        viewModel.updatePlannedCreditsInput("20")
        viewModel.updateTargetGpaInput("4.2")
        val overScaleValidation = viewModel.uiState.value.targetValidation
        assertEquals(TargetFieldError.GPA_OUT_OF_SCALE_RANGE, overScaleValidation.targetGpaError)
        assertTrue(overScaleValidation.targetExceedsScaleMax)
    }
}
