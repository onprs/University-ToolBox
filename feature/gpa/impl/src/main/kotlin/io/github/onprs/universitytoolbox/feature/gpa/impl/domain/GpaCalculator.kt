package io.github.onprs.universitytoolbox.feature.gpa.impl.domain

import kotlin.math.sqrt

/**
 * 多标准 GPA 换算与目标绩点反推纯计算引擎。
 */
object GpaCalculator {
    private const val MIN_SCORE = 0.0
    private const val MAX_SCORE = 100.0
    private const val PASS_SCORE = 60.0
    private const val PKU_QUADRATIC_COEFF = 3.0
    private const val PKU_QUADRATIC_DIVISOR = 1600.0
    private const val FIVE_POINT_OFFSET = 50.0
    private const val FIVE_POINT_STEP = 10.0
    private const val EPSILON = 1e-6

    private val standard40Bands = listOf(
        90.0 to 4.0,
        80.0 to 3.0,
        70.0 to 2.0,
        60.0 to 1.0,
    )

    private val wes40Bands = listOf(
        85.0 to 4.0,
        75.0 to 3.0,
        60.0 to 2.0,
    )

    private val standard43Bands = listOf(
        90.0 to 4.3,
        85.0 to 4.0,
        82.0 to 3.7,
        78.0 to 3.3,
        75.0 to 3.0,
        72.0 to 2.7,
        68.0 to 2.3,
        64.0 to 2.0,
        60.0 to 1.0,
    )

    /**
     * 按指定 [GpaScale] 将百分制成绩转换为单科绩点。
     */
    fun pointForScore(score: Double, scale: GpaScale): Double {
        val clamped = score.coerceIn(MIN_SCORE, MAX_SCORE)
        if (clamped < PASS_SCORE) {
            return 0.0
        }
        return when (scale) {
            GpaScale.PKU_4_0 -> {
                val delta = MAX_SCORE - clamped
                (scale.maxGpa - PKU_QUADRATIC_COEFF * delta * delta / PKU_QUADRATIC_DIVISOR)
                    .coerceIn(0.0, scale.maxGpa)
            }
            GpaScale.STANDARD_4_0 -> matchBandPoint(clamped, standard40Bands)
            GpaScale.WES_4_0 -> matchBandPoint(clamped, wes40Bands)
            GpaScale.STANDARD_4_3 -> matchBandPoint(clamped, standard43Bands)
            GpaScale.FIVE_POINT_5_0 -> {
                ((clamped - FIVE_POINT_OFFSET) / FIVE_POINT_STEP).coerceIn(0.0, scale.maxGpa)
            }
        }
    }

    /**
     * 根据目标单科绩点反推在指定 [GpaScale] 下至少需要取得的百分制分数。
     * 若目标超出该算法满绩点上限则返回 `null`。
     */
    fun minimumScoreForPoint(targetPoint: Double, scale: GpaScale): Double? {
        if (targetPoint <= EPSILON) {
            return 0.0
        }
        if (targetPoint > scale.maxGpa + EPSILON) {
            return null
        }
        val clampedTarget = targetPoint.coerceAtMost(scale.maxGpa)
        return when (scale) {
            GpaScale.PKU_4_0 -> {
                if (clampedTarget <= 1.0 + EPSILON) {
                    PASS_SCORE
                } else {
                    val deltaSquared = (scale.maxGpa - clampedTarget) * PKU_QUADRATIC_DIVISOR / PKU_QUADRATIC_COEFF
                    (MAX_SCORE - sqrt(deltaSquared.coerceAtLeast(0.0))).coerceIn(PASS_SCORE, MAX_SCORE)
                }
            }
            GpaScale.STANDARD_4_0 -> minimumScoreForBand(clampedTarget, standard40Bands)
            GpaScale.WES_4_0 -> minimumScoreForBand(clampedTarget, wes40Bands)
            GpaScale.STANDARD_4_3 -> minimumScoreForBand(clampedTarget, standard43Bands)
            GpaScale.FIVE_POINT_5_0 -> {
                if (clampedTarget <= 1.0 + EPSILON) {
                    PASS_SCORE
                } else {
                    (FIVE_POINT_OFFSET + clampedTarget * FIVE_POINT_STEP).coerceIn(PASS_SCORE, MAX_SCORE)
                }
            }
        }
    }

    /**
     * 汇总计算当前课程列表的加权平均分、算术平均分与综合 GPA。
     */
    fun calculateSummary(courses: List<GpaCourse>, scale: GpaScale): GpaSummary {
        val validCourses = courses.filter { course ->
            course.included && course.credits > 0.0 && course.score.isFinite()
        }
        if (validCourses.isEmpty()) {
            return GpaSummary(
                scale = scale,
                includedCourseCount = 0,
                totalCredits = 0.0,
                gpa = 0.0,
                weightedAverageScore = 0.0,
                arithmeticAverageScore = 0.0,
                breakdowns = emptyList(),
            )
        }

        var totalCredits = 0.0
        var totalCreditPoints = 0.0
        var totalWeightedScore = 0.0
        var totalRawScore = 0.0
        val breakdowns = ArrayList<CourseGpaBreakdown>(validCourses.size)

        for (course in validCourses) {
            val normalizedScore = course.score.coerceIn(MIN_SCORE, MAX_SCORE)
            val point = pointForScore(normalizedScore, scale)
            val creditPoints = point * course.credits
            totalCredits += course.credits
            totalCreditPoints += creditPoints
            totalWeightedScore += normalizedScore * course.credits
            totalRawScore += normalizedScore
            breakdowns += CourseGpaBreakdown(
                course = course.copy(score = normalizedScore),
                gradePoint = point,
                creditPoints = creditPoints,
            )
        }

        return GpaSummary(
            scale = scale,
            includedCourseCount = validCourses.size,
            totalCredits = totalCredits,
            gpa = totalCreditPoints / totalCredits,
            weightedAverageScore = totalWeightedScore / totalCredits,
            arithmeticAverageScore = totalRawScore / validCourses.size,
            breakdowns = breakdowns,
        )
    }

    /**
     * 根据已修学分、当前 GPA 与未来计划修读学分，反推后续所需平均 GPA 与最低均分要求。
     */
    fun projectTargetGpa(request: TargetGpaRequest): TargetGpaProjection {
        if (!isValidTargetRequest(request)) {
            return TargetGpaProjection(
                reachability = TargetReachability.INVALID_INPUT,
                requiredFutureGpa = 0.0,
                requiredMinimumScore = null,
                maxAchievableGpa = 0.0,
            )
        }

        val totalCredits = request.completedCredits + request.plannedCredits
        val maxAchievableGpa = (
            request.currentGpa * request.completedCredits +
                request.scale.maxGpa * request.plannedCredits
            ) / totalCredits
        val rawRequiredFutureGpa = (
            request.targetGpa * totalCredits -
                request.currentGpa * request.completedCredits
            ) / request.plannedCredits

        val reachability = classifyReachability(rawRequiredFutureGpa, request.scale.maxGpa)
        val requiredScore = resolveRequiredScore(reachability, rawRequiredFutureGpa, request.scale)

        return TargetGpaProjection(
            reachability = reachability,
            requiredFutureGpa = rawRequiredFutureGpa.coerceAtLeast(0.0),
            requiredMinimumScore = requiredScore,
            maxAchievableGpa = maxAchievableGpa,
        )
    }

    private fun isValidTargetRequest(request: TargetGpaRequest): Boolean {
        val valuesFinite = request.completedCredits.isFinite() &&
            request.currentGpa.isFinite() &&
            request.plannedCredits.isFinite() &&
            request.targetGpa.isFinite()
        val boundsValid = request.completedCredits >= 0.0 &&
            request.plannedCredits > 0.0 &&
            request.currentGpa in 0.0..(request.scale.maxGpa + EPSILON) &&
            request.targetGpa >= 0.0
        return valuesFinite && boundsValid
    }

    private fun classifyReachability(requiredFutureGpa: Double, maxScaleGpa: Double): TargetReachability = when {
        requiredFutureGpa <= EPSILON -> TargetReachability.ALREADY_SECURED
        requiredFutureGpa > maxScaleGpa + EPSILON -> TargetReachability.UNREACHABLE
        requiredFutureGpa >= maxScaleGpa - EPSILON -> TargetReachability.EXACT_MAX_REQUIRED
        else -> TargetReachability.ACHIEVABLE
    }

    private fun resolveRequiredScore(
        reachability: TargetReachability,
        requiredFutureGpa: Double,
        scale: GpaScale,
    ): Double? = when (reachability) {
        TargetReachability.ALREADY_SECURED -> 0.0
        TargetReachability.UNREACHABLE, TargetReachability.INVALID_INPUT -> null
        TargetReachability.EXACT_MAX_REQUIRED -> minimumScoreForPoint(scale.maxGpa, scale)
        TargetReachability.ACHIEVABLE -> minimumScoreForPoint(requiredFutureGpa, scale)
    }

    private fun matchBandPoint(score: Double, bands: List<Pair<Double, Double>>): Double =
        bands.firstOrNull { (threshold, _) -> score >= threshold }?.second ?: 0.0

    private fun minimumScoreForBand(targetPoint: Double, bands: List<Pair<Double, Double>>): Double? {
        val sortedAscending = bands.sortedBy { (_, point) -> point }
        return sortedAscending.firstOrNull { (_, point) -> point + EPSILON >= targetPoint }?.first
    }
}
