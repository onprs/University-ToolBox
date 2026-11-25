package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.common.security.Redactor
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.CoordinateConverter
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 超星静态图片地址归一化工具：补全协议或站点前缀，并将超星系域名下的明文 HTTP 升级为 HTTPS。
 */
object ChaoxingUrlNormalizer {
    private val HTTPS_UPGRADEABLE_SUFFIXES = listOf(
        "chaoxing.com",
        "cldisk.com",
        "cxwfw.com",
        "xuexitong.com",
    )

    fun normalizeImageUrl(
        rawUrl: String?,
        defaultBaseUrl: String = ChaoxingEndpointConfig.OFFICIAL_MOOC2_ANS_BASE,
    ): String {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return ""
        }
        val candidate = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "${defaultBaseUrl.trimEnd('/')}$trimmed"
            else -> trimmed
        }
        val parsed = candidate.toHttpUrlOrNull() ?: return candidate
        val host = parsed.host.lowercase()
        val shouldUpgrade = parsed.scheme.equals("http", ignoreCase = true) &&
            HTTPS_UPGRADEABLE_SUFFIXES.any { suffix -> host == suffix || host.endsWith(".$suffix") }
        return if (shouldUpgrade) {
            parsed.newBuilder().scheme("https").build().toString()
        } else {
            parsed.toString()
        }
    }
}

/**
 * 已认证学习通账号的脱敏资料模型。
 */
data class ChaoxingAccountProfile(
    val uid: String,
    val fid: String,
    val displayName: String,
    val nickName: String,
    val maskedPhone: String,
    val schoolName: String,
    val avatarUrl: String,
    val studentCode: String,
)

/**
 * 课程领域模型（由移动端 JSON 或新版 Web HTML 防腐层统一产出）。
 */
data class ChaoxingCourse(
    val courseId: Long,
    val clazzId: Long,
    val cpi: Long,
    val name: String,
    val className: String,
    val teacherName: String,
    val coverUrl: String,
    val studentCount: Int,
    val roleType: Int,
    val isArchived: Boolean,
    val isStarted: Boolean,
    val isPinned: Boolean,
)

/**
 * 签到活动子类型。
 */
enum class ChaoxingSignType {
    NORMAL,
    PHOTO,
    QR_CODE,
    GESTURE,
    LOCATION,
    SIGN_CODE,
    SIGN_OUT,
    OTHER,
}

/**
 * 活动生命周期状态。
 */
enum class ChaoxingActivityStatus {
    NOT_STARTED,
    IN_PROGRESS,
    ENDED,
    UNKNOWN,
}

/**
 * 当前登录学生在单次活动中的参与状态。
 */
enum class ChaoxingUserSignStatus {
    UNSIGNED,
    SIGNED,
    TEACHER_SIGNED,
    LEAVE,
    ABSENT,
    SICK_LEAVE,
    PERSONAL_LEAVE,
    LATE,
    EARLY_LEAVE,
    EXPIRED,
    PUBLIC_LEAVE,
    UNKNOWN,
}

/**
 * 课程内学习/签到活动摘要。
 */
data class ChaoxingActivity(
    val activeId: Long,
    val courseId: Long,
    val clazzId: Long,
    val title: String,
    val activeType: Int,
    val otherId: String,
    val signType: ChaoxingSignType,
    val isSignOut: Boolean,
    val activityStatus: ChaoxingActivityStatus,
    val userStatus: ChaoxingUserSignStatus,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val displayTime: String,
    val releaseNum: Int,
    val attendNum: Int,
) {
    val isSignRelated: Boolean
        get() = activeType == ACTIVE_TYPE_SIGN || activeType == ACTIVE_TYPE_SIGN_OUT

    companion object {
        const val ACTIVE_TYPE_SIGN = 2
        const val ACTIVE_TYPE_SIGN_OUT = 74
    }
}

/**
 * 签到活动详情配置模型，由 `getPPTActiveInfo` 反腐层映射得到。
 */
data class ChaoxingActiveDetail(
    val activeId: Long,
    val clazzId: Long,
    val title: String,
    val activeType: Int,
    val otherId: Int,
    val signType: ChaoxingSignType,
    val isSignOut: Boolean,
    val activityStatus: ChaoxingActivityStatus,
    val requiresPhoto: Boolean,
    val requiresLocation: Boolean,
    val requiresCaptcha: Boolean,
    val dynamicQr: Boolean,
    val qrRefreshSeconds: Int,
    val preventCheat: Boolean,
    val requireFace: Boolean,
    val requireWeChat: Boolean,
    val locationText: String?,
    val locationRangeMeters: Int?,
    val targetLatitude: Double?,
    val targetLongitude: Double?,
    val targetLatitudeGd: Double?,
    val targetLongitudeGd: Double?,
    val signInId: Long?,
    val signOutDurationMs: Long?,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val serverNowMs: Long?,
    val releaseNum: Int,
    val attendNum: Int,
) {
    /** 当前签到活动是否在服务端响应中公开了有效的目标地点经纬度。 */
    val hasTargetCoordinates: Boolean
        get() = CoordinateConverter.isValidTargetCoordinate(targetLatitude, targetLongitude)
}

/**
 * 单次签到活动的学生个人真实考勤记录摘要（由 `/v2/apis/sign/getAttendInfo` 产出）。
 */
data class ChaoxingAttendRecord(
    val recordId: Long?,
    val activeId: Long,
    val userStatus: ChaoxingUserSignStatus,
    val deviceCode: String? = null,
    val userAgent: String? = null,
)

/**
 * 学校检索结果项。
 */
data class ChaoxingSchoolOption(val schoolId: Long, val fid: String, val name: String)

/**
 * 登录结果联合类型，区分成功、验证码挂起、双因子与凭据错误。
 */
sealed interface ChaoxingLoginOutcome {
    data class Authenticated(val accountId: String, val profile: ChaoxingAccountProfile) : ChaoxingLoginOutcome

    data class RequiresCaptcha(val message: String) : ChaoxingLoginOutcome

    data class RequiresTwoFactor(val twoFactorUrl: String, val isMobileUrl: Boolean) : ChaoxingLoginOutcome

    data class WeakPasswordRequired(val uid: String, val passwordTimeout: Int?) : ChaoxingLoginOutcome

    data class InvalidCredentials(val message: String) : ChaoxingLoginOutcome

    data class Failed(val error: DomainError) : ChaoxingLoginOutcome
}

/**
 * 桌面端扫码登录临时会话。
 */
data class ChaoxingQrLoginSession(
    val provisionalAccountId: String,
    val uuid: String,
    val enc: String,
    val qrImageUrl: String,
)

/**
 * 桌面端扫码登录轮询状态。
 */
sealed interface ChaoxingQrLoginStatus {
    data object Waiting : ChaoxingQrLoginStatus

    data class ScannedPendingConfirm(val nickname: String?, val uid: String?) : ChaoxingQrLoginStatus

    data class Confirmed(val accountId: String, val profile: ChaoxingAccountProfile) : ChaoxingQrLoginStatus

    data object Cancelled : ChaoxingQrLoginStatus

    data object Expired : ChaoxingQrLoginStatus

    data class Failed(val message: String) : ChaoxingQrLoginStatus
}

/**
 * 课堂二维码签到内容解析结果。
 */
data class ChaoxingQrPayload(val activeId: Long?, val code: String?, val enc: String, val rawContent: String)

/**
 * `stuSignajax` 纯文本提交响应解析结果。
 */
sealed interface ChaoxingSignRawOutcome {
    data class Success(val message: String) : ChaoxingSignRawOutcome

    data class AlreadySigned(val message: String) : ChaoxingSignRawOutcome

    data class RequiresCaptcha(val message: String) : ChaoxingSignRawOutcome

    data class QrExpired(val message: String) : ChaoxingSignRawOutcome

    data class InvalidCode(val message: String) : ChaoxingSignRawOutcome

    data class OutOfRange(val message: String) : ChaoxingSignRawOutcome

    data class PhotoAuthError(val message: String) : ChaoxingSignRawOutcome

    data class Ended(val message: String) : ChaoxingSignRawOutcome

    data class DuplicateDeviceCode(val message: String) : ChaoxingSignRawOutcome

    data class SessionExpired(val message: String) : ChaoxingSignRawOutcome

    data class Rejected(val message: String) : ChaoxingSignRawOutcome
}

/**
 * 二维码内容解析器，兼容官方 `/widget/sign/e` URL、微信跳转 URL 与 `SIGNIN:` 键值对格式。
 */
object ChaoxingQrParser {
    private const val SIGNIN_PREFIX = "SIGNIN:"

    /** 从扫码文本中提取活动 ID、Code 与动态校验串 enc；无法识别时返回 null。 */
    fun parse(rawContent: String): ChaoxingQrPayload? {
        val trimmed = rawContent.trim()
        return when {
            trimmed.isEmpty() -> null
            trimmed.startsWith(SIGNIN_PREFIX, ignoreCase = true) ->
                parseSigninPairs(trimmed.substring(SIGNIN_PREFIX.length), trimmed)
            trimmed.toHttpUrlOrNull() != null ->
                parseHttpQrUrl(trimmed.toHttpUrlOrNull()!!, trimmed)
            trimmed.contains("enc=", ignoreCase = true) ->
                parseSigninPairs(trimmed, trimmed)
            else -> null
        }
    }

    private fun parseHttpQrUrl(httpUrl: okhttp3.HttpUrl, rawContent: String): ChaoxingQrPayload? {
        val nestedRcode = httpUrl.queryParameter("rcode")
        val fromRcode = nestedRcode
            ?.takeIf { it.isNotBlank() }
            ?.let { parse(URLDecoder.decode(it, StandardCharsets.UTF_8)) }
            ?.copy(rawContent = rawContent)
        if (fromRcode != null) {
            return fromRcode
        }
        val enc = httpUrl.queryParameter("enc")?.trim().orEmpty()
        return if (enc.isEmpty()) {
            null
        } else {
            val activeId = (httpUrl.queryParameter("id") ?: httpUrl.queryParameter("activeId"))?.toLongOrNull()
            val code = httpUrl.queryParameter("c") ?: httpUrl.queryParameter("Code")
            ChaoxingQrPayload(
                activeId = activeId,
                code = code,
                enc = enc,
                rawContent = rawContent,
            )
        }
    }

    private fun parseSigninPairs(queryPart: String, rawContent: String): ChaoxingQrPayload? {
        val params = LinkedHashMap<String, String>()
        queryPart.split('&').forEach { segment ->
            val index = segment.indexOf('=')
            if (index > 0) {
                val key = segment.substring(0, index).trim().lowercase()
                val value = URLDecoder.decode(segment.substring(index + 1).trim(), StandardCharsets.UTF_8)
                params[key] = value
            }
        }
        val enc = params["enc"].orEmpty()
        if (enc.isEmpty()) {
            return null
        }
        val activeId = (params["aid"] ?: params["id"] ?: params["activeid"])?.toLongOrNull()
        val code = params["code"] ?: params["c"]
        return ChaoxingQrPayload(
            activeId = activeId,
            code = code,
            enc = enc,
            rawContent = rawContent,
        )
    }
}

/**
 * `stuSignajax` 纯文本状态串解析器。
 */
object ChaoxingSignResponseParser {
    private const val PHOTO_ERROR_CODE = "photoAuthError_PCR007"
    private const val CODE_ERROR_MARKER = "90002"
    private const val LOCATION_ERROR_PREFIX = "errorLocation"

    private data class OutcomeRule(val matches: (String) -> Boolean, val factory: (String) -> ChaoxingSignRawOutcome)

    private val outcomeRules: List<OutcomeRule> = listOf(
        OutcomeRule(
            matches = { text -> text.equals("success", ignoreCase = true) || text.contains("签到成功") },
            factory = { text -> ChaoxingSignRawOutcome.Success(text.ifEmpty { "签到成功" }) },
        ),
        OutcomeRule(
            matches = { text -> text.contains("您已签到过了") || text.contains("已签到") },
            factory = { text -> ChaoxingSignRawOutcome.AlreadySigned(text) },
        ),
        OutcomeRule(
            matches = { text -> text.contains(PHOTO_ERROR_CODE, ignoreCase = true) },
            factory = { text -> ChaoxingSignRawOutcome.PhotoAuthError(text) },
        ),
        OutcomeRule(
            matches = { text -> text.contains("重新扫描") },
            factory = { text -> ChaoxingSignRawOutcome.QrExpired(text) },
        ),
        OutcomeRule(
            matches = { text ->
                text.contains(CODE_ERROR_MARKER) || text.contains("手势不正确") || text.contains("签到码")
            },
            factory = { text -> ChaoxingSignRawOutcome.InvalidCode(text) },
        ),
        OutcomeRule(
            matches = { text ->
                text.contains("不在可签到范围内") ||
                    text.contains("指定签到地点") ||
                    text.startsWith(LOCATION_ERROR_PREFIX, ignoreCase = true)
            },
            factory = { text ->
                val formatted = if (text.startsWith(LOCATION_ERROR_PREFIX, ignoreCase = true)) {
                    val distance = text.substringAfter('_', "").trim()
                    if (distance.isNotEmpty()) "超出指定签到范围（距离约 ${distance}米）" else "超出指定签到范围"
                } else {
                    text
                }
                ChaoxingSignRawOutcome.OutOfRange(formatted)
            },
        ),
        OutcomeRule(
            matches = { text -> text.contains("已结束") },
            factory = { text -> ChaoxingSignRawOutcome.Ended(text) },
        ),
        OutcomeRule(
            matches = { text -> text.contains("同一设备不允许重复签到") },
            factory = { text -> ChaoxingSignRawOutcome.DuplicateDeviceCode(text) },
        ),
        OutcomeRule(
            matches = { text -> text.contains("validate", ignoreCase = true) || text.contains("验证码") },
            factory = { text -> ChaoxingSignRawOutcome.RequiresCaptcha(text) },
        ),
        OutcomeRule(
            matches = { text ->
                text.contains("请重新登录") || text.contains("请登录后再试") || text.contains("非法请求")
            },
            factory = { text -> ChaoxingSignRawOutcome.SessionExpired(text) },
        ),
    )

    /** 将服务端纯文本响应归类为结构化业务状态。 */
    fun parse(rawBody: String): ChaoxingSignRawOutcome {
        val text = rawBody.trim()
        val matched = outcomeRules.firstOrNull { rule -> rule.matches(text) }
        return matched?.factory?.invoke(text)
            ?: ChaoxingSignRawOutcome.Rejected(text.ifEmpty { "未知签到响应" })
    }
}

/**
 * DTO 到领域模型转换器。
 */
object ChaoxingDomainMapper {
    private const val STATUS_NOT_STARTED = 0
    private const val STATUS_IN_PROGRESS = 1
    private const val STATUS_ENDED = 2

    private const val USER_UNSIGNED = 0
    private const val USER_SIGNED = 1
    private const val USER_TEACHER_SIGNED = 2
    private const val USER_LEAVE = 4
    private const val USER_ABSENT = 5
    private const val USER_SICK_LEAVE = 7
    private const val USER_PERSONAL_LEAVE = 8
    private const val USER_LATE = 9
    private const val USER_EARLY_LEAVE = 10
    private const val USER_EXPIRED = 11
    private const val USER_PUBLIC_LEAVE = 12

    private const val OTHER_NORMAL = "0"
    private const val OTHER_QR = "2"
    private const val OTHER_GESTURE = "3"
    private const val OTHER_LOCATION = "4"
    private const val OTHER_CODE = "5"

    private const val CHANNEL_COURSE_NAME = "课程"

    /** 从 SSO 用户信息接口提取脱敏账号资料。 */
    fun toAccountProfile(
        msg: UserLogin4UnameMsgDto,
        endpointConfig: ChaoxingEndpointConfig,
        fallbackPhone: String = "",
    ): ChaoxingAccountProfile {
        val uidStr = msg.uid.toString()
        val rawPhone = msg.phone.orEmpty().ifBlank { fallbackPhone }
        val displayName = msg.name.orEmpty().ifBlank { msg.nick.orEmpty().ifBlank { uidStr } }
        val rawAvatar = msg.pic.orEmpty().ifBlank { endpointConfig.avatarUrl(uidStr) }
        return ChaoxingAccountProfile(
            uid = uidStr,
            fid = msg.fid.toString(),
            displayName = displayName,
            nickName = msg.nick.orEmpty().ifBlank { displayName },
            maskedPhone = Redactor.maskPhone(rawPhone),
            schoolName = msg.schoolname.orEmpty(),
            avatarUrl = ChaoxingUrlNormalizer.normalizeImageUrl(rawAvatar, endpointConfig.photoBaseUrl),
            studentCode = msg.studentcode.orEmpty(),
        )
    }

    /** 将 `backclazzdata` 响应转换为标准化课程列表。 */
    fun toCourses(response: BackClazzDataResponseDto): List<ChaoxingCourse> =
        response.channelList.mapNotNull { channel ->
            if (channel.cataName != CHANNEL_COURSE_NAME) {
                return@mapNotNull null
            }
            val content = channel.content ?: return@mapNotNull null
            val courseData = content.course?.data?.firstOrNull() ?: return@mapNotNull null
            val clazzId = content.id.takeIf { it > 0L } ?: channel.key
            val cpi = channel.cpi.takeIf { it > 0L } ?: content.cpi
            ChaoxingCourse(
                courseId = courseData.id,
                clazzId = clazzId,
                cpi = cpi,
                name = courseData.name.orEmpty().trim(),
                className = content.name.orEmpty().trim(),
                teacherName = courseData.teacherfactor.orEmpty().trim(),
                coverUrl = ChaoxingUrlNormalizer.normalizeImageUrl(courseData.imageurl),
                studentCount = content.studentcount,
                roleType = content.roletype,
                isArchived = content.isFiled == 1 || content.state != 0,
                isStarted = content.isstart,
                isPinned = channel.topsign == 1,
            )
        }

    /**
     * 将活动条目 DTO 映射为领域模型。
     * `activelist` 响应不携带个人真实签到状态，初始标记为 [ChaoxingUserSignStatus.UNKNOWN]，由 `getAttendInfo` 回填。
     */
    fun toActivity(dto: ActiveItemDto, courseId: Long, clazzId: Long): ChaoxingActivity {
        val otherIdStr = dto.otherId.orEmpty()
        val isSignOut = dto.activeType == ChaoxingActivity.ACTIVE_TYPE_SIGN_OUT
        val signType = resolveSignType(dto.activeType, otherIdStr, ifPhoto = false, ifOpenAddress = false)
        return ChaoxingActivity(
            activeId = dto.id,
            courseId = courseId,
            clazzId = clazzId,
            title = dto.nameOne.orEmpty().ifBlank { "未命名活动" },
            activeType = dto.activeType,
            otherId = otherIdStr,
            signType = signType,
            isSignOut = isSignOut,
            activityStatus = mapActivityStatus(dto.status),
            userStatus = ChaoxingUserSignStatus.UNKNOWN,
            startTimeMs = dto.startTime,
            endTimeMs = dto.endTime,
            displayTime = dto.nameFour.orEmpty(),
            releaseNum = dto.releaseNum,
            attendNum = dto.attendNum,
        )
    }

    /** 将 `getAttendInfo` 返回的个人考勤详情映射为领域记录。 */
    fun toAttendRecord(dto: AttendInfoDataDto, fallbackActiveId: Long): ChaoxingAttendRecord = ChaoxingAttendRecord(
        recordId = dto.id,
        activeId = dto.activeId.takeIf { it > 0L } ?: fallbackActiveId,
        userStatus = mapUserSignStatus(dto.status),
        deviceCode = dto.deviceCode?.trim()?.takeIf { it.isNotEmpty() },
        userAgent = dto.useragent?.trim()?.takeIf { it.isNotEmpty() },
    )

    /** 将 `getPPTActiveInfo` 详情响应转换为签到策略执行上下文。 */
    fun toActiveDetail(dto: PptActiveInfoDto): ChaoxingActiveDetail {
        val requiresPhoto = dto.ifphoto == 1
        val requiresLocation = dto.otherId.toString() == OTHER_LOCATION || dto.ifopenAddress == 1
        val isSignOut = dto.activeType == ChaoxingActivity.ACTIVE_TYPE_SIGN_OUT
        val signType = resolveSignType(
            activeType = dto.activeType,
            otherId = dto.otherId.toString(),
            ifPhoto = requiresPhoto,
            ifOpenAddress = requiresLocation,
        )
        val rawBdLat = dto.locationLatitude?.toDoubleOrNull() ?: dto.latitude?.toDoubleOrNull()
        val rawBdLng = dto.locationLongitude?.toDoubleOrNull() ?: dto.longitude?.toDoubleOrNull()
        val rawGdLat = dto.locationLatitudeGd?.toDoubleOrNull()
        val rawGdLng = dto.locationLongitudeGd?.toDoubleOrNull()

        val validGd = if (CoordinateConverter.isValidTargetCoordinate(rawGdLat, rawGdLng)) {
            rawGdLat!! to rawGdLng!!
        } else {
            null
        }
        val validBd = when {
            CoordinateConverter.isValidTargetCoordinate(rawBdLat, rawBdLng) -> rawBdLat!! to rawBdLng!!
            validGd != null -> {
                val converted = CoordinateConverter.gcj02ToBd09(validGd.first, validGd.second)
                converted.latitude to converted.longitude
            }
            else -> null
        }

        return ChaoxingActiveDetail(
            activeId = dto.id,
            clazzId = dto.clazzid,
            title = dto.name.orEmpty().ifBlank { "签到活动" },
            activeType = dto.activeType,
            otherId = dto.otherId,
            signType = signType,
            isSignOut = isSignOut,
            activityStatus = mapActivityStatus(dto.status),
            requiresPhoto = requiresPhoto,
            requiresLocation = requiresLocation,
            requiresCaptcha = dto.ifNeedVCode == 1 || dto.showVCode == 1,
            dynamicQr = dto.ifrefreshewm == 1,
            qrRefreshSeconds = dto.ewmRefreshTime,
            preventCheat = dto.openPreventCheatFlag == 1,
            requireFace = dto.openCheckFaceFlag == 1,
            requireWeChat = dto.openCheckWeChatFlag == 1,
            locationText = dto.locationText?.takeIf { it.isNotBlank() },
            locationRangeMeters = dto.locationRange?.toIntOrNull(),
            targetLatitude = validBd?.first,
            targetLongitude = validBd?.second,
            targetLatitudeGd = validGd?.first,
            targetLongitudeGd = validGd?.second,
            signInId = dto.signInId,
            signOutDurationMs = dto.signOutDuration,
            startTimeMs = dto.starttime,
            endTimeMs = dto.endtime,
            serverNowMs = dto.nowTime,
            releaseNum = dto.releaseNum,
            attendNum = dto.attendNum,
        )
    }

    private fun resolveSignType(
        activeType: Int,
        otherId: String,
        ifPhoto: Boolean,
        ifOpenAddress: Boolean,
    ): ChaoxingSignType = when {
        activeType == ChaoxingActivity.ACTIVE_TYPE_SIGN_OUT -> ChaoxingSignType.SIGN_OUT
        activeType != ChaoxingActivity.ACTIVE_TYPE_SIGN -> ChaoxingSignType.OTHER
        ifPhoto -> ChaoxingSignType.PHOTO
        otherId == OTHER_QR -> ChaoxingSignType.QR_CODE
        otherId == OTHER_GESTURE -> ChaoxingSignType.GESTURE
        otherId == OTHER_LOCATION || ifOpenAddress -> ChaoxingSignType.LOCATION
        otherId == OTHER_CODE -> ChaoxingSignType.SIGN_CODE
        otherId == OTHER_NORMAL || otherId.isBlank() -> ChaoxingSignType.NORMAL
        else -> ChaoxingSignType.NORMAL
    }

    private fun mapActivityStatus(status: Int): ChaoxingActivityStatus = when (status) {
        STATUS_NOT_STARTED -> ChaoxingActivityStatus.NOT_STARTED
        STATUS_IN_PROGRESS -> ChaoxingActivityStatus.IN_PROGRESS
        STATUS_ENDED -> ChaoxingActivityStatus.ENDED
        else -> ChaoxingActivityStatus.UNKNOWN
    }

    private fun mapUserSignStatus(userStatus: Int): ChaoxingUserSignStatus = when (userStatus) {
        USER_UNSIGNED -> ChaoxingUserSignStatus.UNSIGNED
        USER_SIGNED -> ChaoxingUserSignStatus.SIGNED
        USER_TEACHER_SIGNED -> ChaoxingUserSignStatus.TEACHER_SIGNED
        USER_LEAVE -> ChaoxingUserSignStatus.LEAVE
        USER_ABSENT -> ChaoxingUserSignStatus.ABSENT
        USER_SICK_LEAVE -> ChaoxingUserSignStatus.SICK_LEAVE
        USER_PERSONAL_LEAVE -> ChaoxingUserSignStatus.PERSONAL_LEAVE
        USER_LATE -> ChaoxingUserSignStatus.LATE
        USER_EARLY_LEAVE -> ChaoxingUserSignStatus.EARLY_LEAVE
        USER_EXPIRED -> ChaoxingUserSignStatus.EXPIRED
        USER_PUBLIC_LEAVE -> ChaoxingUserSignStatus.PUBLIC_LEAVE
        else -> ChaoxingUserSignStatus.UNKNOWN
    }
}
