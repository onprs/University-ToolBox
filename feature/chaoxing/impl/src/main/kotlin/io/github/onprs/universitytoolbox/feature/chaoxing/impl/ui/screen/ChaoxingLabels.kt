package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActivityStatus
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingSignType
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingUserSignStatus
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun ChaoxingSignType.displayLabel(): String = when (this) {
    ChaoxingSignType.NORMAL -> "普通签到"
    ChaoxingSignType.PHOTO -> "拍照签到"
    ChaoxingSignType.QR_CODE -> "二维码签到"
    ChaoxingSignType.GESTURE -> "手势签到"
    ChaoxingSignType.LOCATION -> "位置签到"
    ChaoxingSignType.SIGN_CODE -> "签到码签到"
    ChaoxingSignType.SIGN_OUT -> "课堂签退"
    ChaoxingSignType.OTHER -> "课程活动"
}

internal fun ChaoxingActivityStatus.displayLabel(): String = when (this) {
    ChaoxingActivityStatus.NOT_STARTED -> "未开始"
    ChaoxingActivityStatus.IN_PROGRESS -> "进行中"
    ChaoxingActivityStatus.ENDED -> "已结束"
    ChaoxingActivityStatus.UNKNOWN -> "未知状态"
}

internal fun ChaoxingUserSignStatus.displayLabel(): String = when (this) {
    ChaoxingUserSignStatus.UNSIGNED -> "未签"
    ChaoxingUserSignStatus.SIGNED -> "已签"
    ChaoxingUserSignStatus.TEACHER_SIGNED -> "教师代签"
    ChaoxingUserSignStatus.LEAVE -> "请假"
    ChaoxingUserSignStatus.ABSENT -> "缺勤"
    ChaoxingUserSignStatus.SICK_LEAVE -> "病假"
    ChaoxingUserSignStatus.PERSONAL_LEAVE -> "事假"
    ChaoxingUserSignStatus.LATE -> "迟到"
    ChaoxingUserSignStatus.EARLY_LEAVE -> "早退"
    ChaoxingUserSignStatus.EXPIRED -> "已过期"
    ChaoxingUserSignStatus.PUBLIC_LEAVE -> "公假"
    ChaoxingUserSignStatus.UNKNOWN -> "待确认"
}

internal fun SignExecutionResult.displayStatusText(): String = when (this) {
    is SignExecutionResult.Success -> "签到成功：$message"
    is SignExecutionResult.AlreadySigned -> "已签到：$message"
    is SignExecutionResult.RequiresCaptcha -> "待行为验证：$message"
    is SignExecutionResult.RequiresExtraInput -> "缺少参数：$message"
    is SignExecutionResult.SessionExpired -> "会话过期：$message"
    is SignExecutionResult.QuotaRestricted -> "配额受限：$message"
    is SignExecutionResult.Rejected -> "未通过：$message"
    is SignExecutionResult.Failed -> "执行失败：${error.message}"
}

internal fun formatTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) {
        return "--"
    }
    return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs))
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
