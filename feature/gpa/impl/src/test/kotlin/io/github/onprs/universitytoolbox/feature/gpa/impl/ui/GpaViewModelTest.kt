package io.github.onprs.universitytoolbox.feature.gpa.impl.ui

import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.GpaScale
import io.github.onprs.universitytoolbox.feature.gpa.impl.domain.TargetReachability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

        viewModel.saveCourse(name = "Physics", credits = 2.0, score = 80.0)
        assertEquals(2, viewModel.uiState.value.courses.size)

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

        viewModel.openEditCourseDialog(created)
        viewModel.saveCourse("Advanced Math", 4.0, 95.0)

        assertEquals(1, viewModel.uiState.value.courses.size)
        val edited = viewModel.uiState.value.courses.single()
        assertEquals(created.id, edited.id)
        assertEquals("Advanced Math", edited.name)
        assertEquals(4.0, edited.credits, 1e-4)
        assertEquals(95.0, edited.score, 1e-4)
    }
}
