package io.github.onprs.universitytoolbox.feature.settings.domain

/**
 * 版本更新检查结果分类。
 */
sealed interface UpdateAssessment {
    data object UpToDate : UpdateAssessment

    data class OptionalUpdate(val latestVersionCode: Long, val latestVersionName: String, val changelog: String) :
        UpdateAssessment

    data class ForceUpdateRequired(
        val latestVersionCode: Long,
        val latestVersionName: String,
        val minSupportedVersionCode: Long,
        val changelog: String,
    ) : UpdateAssessment
}

/**
 * 根据当前宿主版本号与远端清单版本门槛判定更新策略。
 */
object UpdatePolicyEvaluator {
    fun evaluate(
        currentVersionCode: Long,
        latestVersionCode: Long,
        latestVersionName: String,
        minSupportedVersionCode: Long,
        changelog: String,
    ): UpdateAssessment = when {
        currentVersionCode < minSupportedVersionCode -> UpdateAssessment.ForceUpdateRequired(
            latestVersionCode = latestVersionCode,
            latestVersionName = latestVersionName,
            minSupportedVersionCode = minSupportedVersionCode,
            changelog = changelog,
        )
        latestVersionCode > currentVersionCode -> UpdateAssessment.OptionalUpdate(
            latestVersionCode = latestVersionCode,
            latestVersionName = latestVersionName,
            changelog = changelog,
        )
        else -> UpdateAssessment.UpToDate
    }
}
