package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign

import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActiveDetail
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingEndpointConfig
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingSignRawOutcome
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingSignType

/**
 * 签到执行所需的账号最小身份上下文。
 */
data class SignAccountIdentity(
    val accountId: String,
    val uid: String,
    val fid: String,
    val maskedAccount: String,
    val displayName: String,
    val deviceCode: String = "",
    val clientUserAgent: String? = null,
)

/**
 * 签到执行前缺少的必要用户参数类型。
 */
enum class SignMissingField {
    QR_CODE_ENC,
    SIGN_CODE,
    LOCATION_COORDINATE,
    PHOTO_IMAGE,
}

/**
 * 统一签到执行参数。
 */
data class SignExecutionRequest(
    val courseId: Long,
    val clazzId: Long,
    val activeId: Long,
    val enc: String? = null,
    val rawQrContent: String? = null,
    val signCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val photoBytes: ByteArray? = null,
    val photoFileName: String? = null,
    val preUploadedObjectId: String? = null,
    val captchaValidate: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignExecutionRequest) return false
        return scalarTuple() == other.scalarTuple() && photoBytes.contentEquals(other.photoBytes)
    }

    override fun hashCode(): Int {
        var result = scalarTuple().hashCode()
        result = HASH_MULTIPLIER * result + (photoBytes?.contentHashCode() ?: 0)
        return result
    }

    private fun scalarTuple(): List<Any?> = listOf(
        courseId,
        clazzId,
        activeId,
        enc,
        rawQrContent,
        signCode,
        latitude,
        longitude,
        address,
        photoFileName,
        preUploadedObjectId,
        captchaValidate,
    )

    companion object {
        private const val HASH_MULTIPLIER = 31
    }
}

/**
 * 单账号单次签到策略执行结果。
 */
sealed interface SignExecutionResult {
    data class Success(val message: String) : SignExecutionResult

    data class AlreadySigned(val message: String) : SignExecutionResult

    /** 触发服务端行为验证码拦截，进入挂起态等待前端传入 `validate` 凭据。 */
    data class RequiresCaptcha(val captchaId: String, val activeId: Long, val message: String) : SignExecutionResult

    /** 缺少必要输入（二维码 enc、手势/签到码、定位坐标或照片）。 */
    data class RequiresExtraInput(val missingField: SignMissingField, val message: String) : SignExecutionResult

    data class SessionExpired(val message: String) : SignExecutionResult

    data class QuotaRestricted(val message: String) : SignExecutionResult

    data class DuplicateDeviceCode(val message: String) : SignExecutionResult

    data class Rejected(val message: String) : SignExecutionResult

    data class Failed(val error: DomainError) : SignExecutionResult
}

/**
 * 插件化签到策略处理器接口。
 */
interface SignActionHandler {
    val supportedTypes: Set<ChaoxingSignType>

    fun canHandle(detail: ChaoxingActiveDetail): Boolean = detail.signType in supportedTypes

    suspend fun execute(
        account: SignAccountIdentity,
        detail: ChaoxingActiveDetail,
        request: SignExecutionRequest,
    ): SignExecutionResult
}

/**
 * 将 `stuSignajax` 原始响应结果映射为领域签到结果。
 */
internal fun ChaoxingSignRawOutcome.toExecutionResult(activeId: Long): SignExecutionResult = when (this) {
    is ChaoxingSignRawOutcome.Success -> SignExecutionResult.Success(message)
    is ChaoxingSignRawOutcome.AlreadySigned -> SignExecutionResult.AlreadySigned(message)
    is ChaoxingSignRawOutcome.RequiresCaptcha -> SignExecutionResult.RequiresCaptcha(
        captchaId = ChaoxingEndpointConfig.DEFAULT_CAPTCHA_ID,
        activeId = activeId,
        message = message,
    )

    is ChaoxingSignRawOutcome.QrExpired -> SignExecutionResult.Rejected(message)
    is ChaoxingSignRawOutcome.InvalidCode -> SignExecutionResult.Rejected(message)
    is ChaoxingSignRawOutcome.OutOfRange -> SignExecutionResult.Rejected(message)
    is ChaoxingSignRawOutcome.PhotoAuthError -> SignExecutionResult.Rejected(message)
    is ChaoxingSignRawOutcome.Ended -> SignExecutionResult.Rejected(message)
    is ChaoxingSignRawOutcome.DuplicateDeviceCode -> SignExecutionResult.DuplicateDeviceCode(message)
    is ChaoxingSignRawOutcome.SessionExpired -> SignExecutionResult.SessionExpired(message)
    is ChaoxingSignRawOutcome.Rejected -> SignExecutionResult.Rejected(message)
}
