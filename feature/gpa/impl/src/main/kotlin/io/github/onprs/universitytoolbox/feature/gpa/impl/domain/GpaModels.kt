package io.github.onprs.universitytoolbox.feature.gpa.impl.domain

/**
 * 单门课程成绩条目。
 */
data class GpaCourse(
    val id: String,
    val name: String,
    val credits: Double,
    val score: Double,
    val included: Boolean = true,
)

/**
 * 单门课程按指定标准换算后的明细结果。
 */
data class CourseGpaBreakdown(val course: GpaCourse, val gradePoint: Double, val creditPoints: Double)

/**
 * 当前全部计入课程的加权绩点统计摘要。
 */
data class GpaSummary(
    val scale: GpaScale,
    val includedCourseCount: Int,
    val totalCredits: Double,
    val gpa: Double,
    val weightedAverageScore: Double,
    val arithmeticAverageScore: Double,
    val breakdowns: List<CourseGpaBreakdown>,
)

/**
 * 目标绩点反推输入参数。
 */
data class TargetGpaRequest(
    val scale: GpaScale,
    val completedCredits: Double,
    val currentGpa: Double,
    val plannedCredits: Double,
    val targetGpa: Double,
)

/**
 * 目标绩点可达性分类。
 */
enum class TargetReachability {
    ALREADY_SECURED,
    ACHIEVABLE,
    EXACT_MAX_REQUIRED,
    UNREACHABLE,
    INVALID_INPUT,
}

/**
 * 目标绩点反推计算结果。
 */
data class TargetGpaProjection(
    val reachability: TargetReachability,
    val requiredFutureGpa: Double,
    val requiredMinimumScore: Double?,
    val maxAchievableGpa: Double,
)
