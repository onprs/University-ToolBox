package io.github.onprs.universitytoolbox.feature.gpa.impl.domain

/**
 * 课程编辑弹窗字段校验错误类型。
 */
enum class CourseFieldError {
    NAME_BLANK,
    INVALID_NUMBER,
    CREDITS_OUT_OF_RANGE,
    SCORE_OUT_OF_RANGE,
}

/**
 * 课程编辑表单完整校验结果。
 */
data class CourseFormValidation(
    val parsedCredits: Double?,
    val parsedScore: Double?,
    val nameError: CourseFieldError?,
    val creditsError: CourseFieldError?,
    val scoreError: CourseFieldError?,
) {
    val isValid: Boolean
        get() = nameError == null &&
            creditsError == null &&
            scoreError == null &&
            parsedCredits != null &&
            parsedScore != null
}

/**
 * 目标绩点反推表单字段错误类型。
 */
enum class TargetFieldError {
    INVALID_NUMBER,
    NEGATIVE_CREDITS,
    CREDITS_TOO_LARGE,
    ZERO_PLANNED_CREDITS,
    GPA_OUT_OF_SCALE_RANGE,
}

/**
 * 目标绩点反推表单综合校验与边界诊断结果。
 */
data class TargetPlannerValidation(
    val completedCreditsError: TargetFieldError? = null,
    val currentGpaError: TargetFieldError? = null,
    val plannedCreditsError: TargetFieldError? = null,
    val targetGpaError: TargetFieldError? = null,
    val isAwaitingBaseInput: Boolean = true,
    val zeroPlannedCurrentGpa: Double? = null,
    val zeroPlannedTargetMet: Boolean = false,
    val targetExceedsScaleMax: Boolean = false,
) {
    val hasFieldError: Boolean
        get() = completedCreditsError != null ||
            currentGpaError != null ||
            plannedCreditsError != null ||
            targetGpaError != null
}

/**
 * GPA 模块数值输入规范化与边界校验工具。
 * 兼容常见逗号小数点、全角点号及粘贴带来的首尾空白。
 */
object GpaInputValidator {
    const val MAX_COURSE_CREDITS = 50.0
    const val MAX_TOTAL_CREDITS = 500.0
    const val MIN_SCORE = 0.0
    const val MAX_SCORE = 100.0
    private const val EPSILON = 1e-6

    /**
     * 解析用户输入的十进制浮点数，支持将 `,`、`，`、`。` 规范化为标准小数点。
     */
    fun parseFlexibleDecimal(rawInput: String): Double? {
        val normalized = rawInput.trim()
            .replace(',', '.')
            .replace('，', '.')
            .replace('。', '.')
        if (normalized.isEmpty()) {
            return null
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /**
     * 校验添加/编辑课程表单输入。
     */
    fun validateCourseForm(
        name: String,
        creditsInput: String,
        scoreInput: String,
        showBlankNameError: Boolean,
    ): CourseFormValidation {
        val trimmedName = name.trim()
        val parsedCredits = parseFlexibleDecimal(creditsInput)
        val parsedScore = parseFlexibleDecimal(scoreInput)

        val nameError = if (trimmedName.isEmpty() && showBlankNameError) {
            CourseFieldError.NAME_BLANK
        } else {
            null
        }
        val creditsError = validateCourseCredits(creditsInput, parsedCredits)
        val scoreError = validateCourseScore(scoreInput, parsedScore)

        return CourseFormValidation(
            parsedCredits = parsedCredits?.takeIf { creditsError == null },
            parsedScore = parsedScore?.takeIf { scoreError == null },
            nameError = if (trimmedName.isEmpty()) nameError ?: CourseFieldError.NAME_BLANK else null,
            creditsError = creditsError,
            scoreError = scoreError,
        ).let { raw ->
            if (!showBlankNameError && trimmedName.isEmpty()) {
                raw.copy(nameError = null, parsedCredits = null, parsedScore = null)
            } else {
                raw
            }
        }
    }

    /**
     * 判断课程提交参数是否处于有效业务范围。
     */
    fun isValidCourseSubmission(name: String, credits: Double, score: Double): Boolean = name.trim().isNotEmpty() &&
        credits.isFinite() &&
        credits > 0.0 &&
        credits <= MAX_COURSE_CREDITS &&
        score.isFinite() &&
        score in MIN_SCORE..MAX_SCORE

    /**
     * 校验目标绩点反推表单输入并返回投影结果与诊断状态。
     */
    fun evaluateTargetInputs(
        scale: GpaScale,
        completedInput: String,
        currentGpaInput: String,
        plannedInput: String,
        targetGpaInput: String,
    ): Pair<TargetPlannerValidation, TargetGpaProjection?> {
        val parsed = parseTargetRawInputs(completedInput, currentGpaInput, plannedInput, targetGpaInput)
        val errors = collectTargetErrors(parsed, scale.maxGpa)
        val validation = buildTargetValidation(parsed, errors, scale.maxGpa)
        val projection = computeProjectionIfReady(scale, parsed, validation)
        return validation to projection
    }

    private data class ParsedTargetInputs(
        val completedRaw: String,
        val currentRaw: String,
        val plannedRaw: String,
        val targetRaw: String,
        val completedVal: Double?,
        val currentVal: Double?,
        val plannedVal: Double?,
        val targetVal: Double?,
    ) {
        val hasEmptyField: Boolean
            get() = completedRaw.isEmpty() ||
                currentRaw.isEmpty() ||
                plannedRaw.isEmpty() ||
                targetRaw.isEmpty()

        fun toRequestOrNull(scale: GpaScale): TargetGpaRequest? {
            val completed = completedVal ?: return null
            val current = currentVal ?: return null
            val planned = plannedVal ?: return null
            val target = targetVal ?: return null
            return TargetGpaRequest(
                scale = scale,
                completedCredits = completed,
                currentGpa = current,
                plannedCredits = planned,
                targetGpa = target,
            )
        }
    }

    private data class TargetFieldErrors(
        val completed: TargetFieldError?,
        val current: TargetFieldError?,
        val planned: TargetFieldError?,
        val target: TargetFieldError?,
    ) {
        val hasAnyError: Boolean
            get() = listOf(completed, current, planned, target).any { it != null }
    }

    private fun parseTargetRawInputs(
        completedInput: String,
        currentGpaInput: String,
        plannedInput: String,
        targetGpaInput: String,
    ): ParsedTargetInputs {
        val completedTrimmed = completedInput.trim()
        val rawCurrentTrimmed = currentGpaInput.trim()
        val plannedTrimmed = plannedInput.trim()
        val targetTrimmed = targetGpaInput.trim()
        val completedVal = parseFlexibleDecimal(completedTrimmed)
        val effectiveCurrent = if (completedVal == 0.0 && rawCurrentTrimmed.isEmpty()) "0" else rawCurrentTrimmed
        return ParsedTargetInputs(
            completedRaw = completedTrimmed,
            currentRaw = effectiveCurrent,
            plannedRaw = plannedTrimmed,
            targetRaw = targetTrimmed,
            completedVal = completedVal,
            currentVal = parseFlexibleDecimal(effectiveCurrent),
            plannedVal = parseFlexibleDecimal(plannedTrimmed),
            targetVal = parseFlexibleDecimal(targetTrimmed),
        )
    }

    private fun collectTargetErrors(parsed: ParsedTargetInputs, maxScaleGpa: Double): TargetFieldErrors =
        TargetFieldErrors(
            completed = validateCompletedCredits(parsed.completedRaw, parsed.completedVal),
            current = validateScaleGpa(parsed.currentRaw, parsed.currentVal, maxScaleGpa),
            planned = validatePlannedCredits(parsed.plannedRaw, parsed.plannedVal),
            target = validateScaleGpa(parsed.targetRaw, parsed.targetVal, maxScaleGpa),
        )

    private fun buildTargetValidation(
        parsed: ParsedTargetInputs,
        errors: TargetFieldErrors,
        maxScaleGpa: Double,
    ): TargetPlannerValidation {
        val zeroPlannedCurrent = resolveZeroPlannedCurrentGpa(parsed, errors)
        val targetVal = parsed.targetVal
        val targetMet = zeroPlannedCurrent != null &&
            targetVal != null &&
            zeroPlannedCurrent + EPSILON >= targetVal
        val exceedsScale = errors.target == TargetFieldError.GPA_OUT_OF_SCALE_RANGE &&
            targetVal != null &&
            targetVal > maxScaleGpa + EPSILON
        return TargetPlannerValidation(
            completedCreditsError = errors.completed,
            currentGpaError = errors.current,
            plannedCreditsError = errors.planned,
            targetGpaError = errors.target,
            isAwaitingBaseInput = parsed.hasEmptyField && !errors.hasAnyError,
            zeroPlannedCurrentGpa = zeroPlannedCurrent,
            zeroPlannedTargetMet = targetMet,
            targetExceedsScaleMax = exceedsScale,
        )
    }

    private fun resolveZeroPlannedCurrentGpa(parsed: ParsedTargetInputs, errors: TargetFieldErrors): Double? {
        if (errors.planned != TargetFieldError.ZERO_PLANNED_CREDITS) return null
        if (errors.completed != null || errors.current != null) return null
        val target = parsed.targetVal ?: return null
        if (target < 0.0 || parsed.completedVal == null) return null
        return parsed.currentVal
    }

    private fun computeProjectionIfReady(
        scale: GpaScale,
        parsed: ParsedTargetInputs,
        validation: TargetPlannerValidation,
    ): TargetGpaProjection? {
        val request = parsed.toRequestOrNull(scale)
        if (request != null) {
            return GpaCalculator.projectTargetGpa(request)
        }
        return if (validation.hasFieldError) {
            TargetGpaProjection(
                reachability = TargetReachability.INVALID_INPUT,
                requiredFutureGpa = 0.0,
                requiredMinimumScore = null,
                maxAchievableGpa = 0.0,
            )
        } else {
            null
        }
    }

    private fun validateCourseCredits(raw: String, parsed: Double?): CourseFieldError? = when {
        raw.isBlank() || parsed == null -> CourseFieldError.INVALID_NUMBER
        parsed <= 0.0 || parsed > MAX_COURSE_CREDITS -> CourseFieldError.CREDITS_OUT_OF_RANGE
        else -> null
    }

    private fun validateCourseScore(raw: String, parsed: Double?): CourseFieldError? = when {
        raw.isBlank() || parsed == null -> CourseFieldError.INVALID_NUMBER
        parsed !in MIN_SCORE..MAX_SCORE -> CourseFieldError.SCORE_OUT_OF_RANGE
        else -> null
    }

    private fun validateCompletedCredits(raw: String, parsed: Double?): TargetFieldError? = when {
        raw.isEmpty() -> null
        parsed == null -> TargetFieldError.INVALID_NUMBER
        parsed < 0.0 -> TargetFieldError.NEGATIVE_CREDITS
        parsed > MAX_TOTAL_CREDITS -> TargetFieldError.CREDITS_TOO_LARGE
        else -> null
    }

    private fun validatePlannedCredits(raw: String, parsed: Double?): TargetFieldError? = when {
        raw.isEmpty() -> null
        parsed == null -> TargetFieldError.INVALID_NUMBER
        parsed < 0.0 -> TargetFieldError.NEGATIVE_CREDITS
        parsed == 0.0 -> TargetFieldError.ZERO_PLANNED_CREDITS
        parsed > MAX_TOTAL_CREDITS -> TargetFieldError.CREDITS_TOO_LARGE
        else -> null
    }

    private fun validateScaleGpa(raw: String, parsed: Double?, maxGpa: Double): TargetFieldError? = when {
        raw.isEmpty() -> null
        parsed == null -> TargetFieldError.INVALID_NUMBER
        parsed < 0.0 || parsed > maxGpa + EPSILON -> TargetFieldError.GPA_OUT_OF_SCALE_RANGE
        else -> null
    }
}
