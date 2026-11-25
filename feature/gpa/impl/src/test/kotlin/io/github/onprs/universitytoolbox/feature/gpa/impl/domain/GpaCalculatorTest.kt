package io.github.onprs.universitytoolbox.feature.gpa.impl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpaCalculatorTest {
    @Test
    fun `pointForScore computes accurate values across all scales`() {
        assertEquals(4.0, GpaCalculator.pointForScore(100.0, GpaScale.PKU_4_0), 1e-4)
        assertEquals(3.7, GpaCalculator.pointForScore(90.0, GpaScale.PKU_4_0), 0.12)
        // 90分按北大公式: 4 - 3 * 100 / 1600 = 3.8125
        assertEquals(3.8125, GpaCalculator.pointForScore(90.0, GpaScale.PKU_4_0), 1e-4)
        assertEquals(1.0, GpaCalculator.pointForScore(60.0, GpaScale.PKU_4_0), 1e-4)
        assertEquals(0.0, GpaCalculator.pointForScore(59.9, GpaScale.PKU_4_0), 1e-4)

        assertEquals(4.0, GpaCalculator.pointForScore(90.0, GpaScale.STANDARD_4_0), 1e-4)
        assertEquals(3.0, GpaCalculator.pointForScore(85.0, GpaScale.STANDARD_4_0), 1e-4)
        assertEquals(2.0, GpaCalculator.pointForScore(72.0, GpaScale.STANDARD_4_0), 1e-4)
        assertEquals(1.0, GpaCalculator.pointForScore(60.0, GpaScale.STANDARD_4_0), 1e-4)

        assertEquals(4.0, GpaCalculator.pointForScore(85.0, GpaScale.WES_4_0), 1e-4)
        assertEquals(3.0, GpaCalculator.pointForScore(78.0, GpaScale.WES_4_0), 1e-4)
        assertEquals(2.0, GpaCalculator.pointForScore(62.0, GpaScale.WES_4_0), 1e-4)

        assertEquals(4.3, GpaCalculator.pointForScore(92.0, GpaScale.STANDARD_4_3), 1e-4)
        assertEquals(3.7, GpaCalculator.pointForScore(83.0, GpaScale.STANDARD_4_3), 1e-4)

        assertEquals(4.5, GpaCalculator.pointForScore(95.0, GpaScale.FIVE_POINT_5_0), 1e-4)
        assertEquals(1.0, GpaCalculator.pointForScore(60.0, GpaScale.FIVE_POINT_5_0), 1e-4)
    }

    @Test
    fun `calculateSummary ignores excluded and invalid credit courses`() {
        val courses = listOf(
            GpaCourse(id = "1", name = "高等数学", credits = 4.0, score = 90.0, included = true),
            GpaCourse(id = "2", name = "线性代数", credits = 2.0, score = 80.0, included = true),
            GpaCourse(id = "3", name = "体育", credits = 1.0, score = 60.0, included = false),
            GpaCourse(id = "4", name = "无效学分", credits = 0.0, score = 100.0, included = true),
        )

        val summary = GpaCalculator.calculateSummary(courses, GpaScale.STANDARD_4_0)

        assertEquals(2, summary.includedCourseCount)
        assertEquals(6.0, summary.totalCredits, 1e-4)
        // (4*4.0 + 2*3.0) / 6 = 22/6 = 3.6667
        assertEquals(22.0 / 6.0, summary.gpa, 1e-4)
        // (4*90 + 2*80) / 6 = 520/6 = 86.6667
        assertEquals(520.0 / 6.0, summary.weightedAverageScore, 1e-4)
        assertEquals(85.0, summary.arithmeticAverageScore, 1e-4)
    }

    @Test
    fun `calculateSummary returns zeroes for empty valid course list`() {
        val summary = GpaCalculator.calculateSummary(emptyList(), GpaScale.PKU_4_0)
        assertEquals(0, summary.includedCourseCount)
        assertEquals(0.0, summary.totalCredits, 1e-4)
        assertEquals(0.0, summary.gpa, 1e-4)
    }

    @Test
    fun `projectTargetGpa solves achievable pku target and verifies round trip`() {
        val projection = GpaCalculator.projectTargetGpa(
            TargetGpaRequest(
                scale = GpaScale.PKU_4_0,
                completedCredits = 60.0,
                currentGpa = 3.2,
                plannedCredits = 40.0,
                targetGpa = 3.45,
            ),
        )

        // (3.45 * 100 - 3.2 * 60) / 40 = (345 - 192) / 40 = 3.825
        assertEquals(TargetReachability.ACHIEVABLE, projection.reachability)
        assertEquals(3.825, projection.requiredFutureGpa, 1e-4)
        val requiredScore = projection.requiredMinimumScore!!
        assertEquals(3.825, GpaCalculator.pointForScore(requiredScore, GpaScale.PKU_4_0), 1e-3)
        // max achievable: (3.2*60 + 4.0*40)/100 = 3.52
        assertEquals(3.52, projection.maxAchievableGpa, 1e-4)
    }

    @Test
    fun `projectTargetGpa detects unreachable already secured and invalid scenarios`() {
        val unreachable = GpaCalculator.projectTargetGpa(
            TargetGpaRequest(
                scale = GpaScale.STANDARD_4_0,
                completedCredits = 80.0,
                currentGpa = 3.0,
                plannedCredits = 20.0,
                targetGpa = 3.5,
            ),
        )
        assertEquals(TargetReachability.UNREACHABLE, unreachable.reachability)
        assertNull(unreachable.requiredMinimumScore)
        assertEquals(3.2, unreachable.maxAchievableGpa, 1e-4)

        val secured = GpaCalculator.projectTargetGpa(
            TargetGpaRequest(
                scale = GpaScale.WES_4_0,
                completedCredits = 80.0,
                currentGpa = 3.8,
                plannedCredits = 20.0,
                targetGpa = 3.0,
            ),
        )
        assertEquals(TargetReachability.ALREADY_SECURED, secured.reachability)
        assertEquals(0.0, secured.requiredMinimumScore!!, 1e-4)

        val exactMax = GpaCalculator.projectTargetGpa(
            TargetGpaRequest(
                scale = GpaScale.FIVE_POINT_5_0,
                completedCredits = 50.0,
                currentGpa = 4.0,
                plannedCredits = 50.0,
                targetGpa = 4.5,
            ),
        )
        assertEquals(TargetReachability.EXACT_MAX_REQUIRED, exactMax.reachability)
        assertEquals(100.0, exactMax.requiredMinimumScore!!, 1e-4)

        val invalid = GpaCalculator.projectTargetGpa(
            TargetGpaRequest(
                scale = GpaScale.STANDARD_4_0,
                completedCredits = 10.0,
                currentGpa = 3.0,
                plannedCredits = 0.0,
                targetGpa = 3.2,
            ),
        )
        assertEquals(TargetReachability.INVALID_INPUT, invalid.reachability)
    }
}
