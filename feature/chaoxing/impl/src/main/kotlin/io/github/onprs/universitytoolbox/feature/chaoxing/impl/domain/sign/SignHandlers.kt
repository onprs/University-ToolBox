package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign

import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActiveDetail
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActivityStatus
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingEndpointConfig
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingQrParser
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingSignType
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.StuSignSubmitParams
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.CoordinateConverter
import javax.inject.Inject

/**
 * 普通点击签到及无拍照要求签退策略处理器。
 */
class NormalSignActionHandler @Inject constructor(private val remoteDataSource: ChaoxingRemoteDataSource) :
    SignActionHandler {
    override val supportedTypes: Set<ChaoxingSignType> = setOf(
        ChaoxingSignType.NORMAL,
        ChaoxingSignType.SIGN_OUT,
    )

    override fun canHandle(detail: ChaoxingActiveDetail): Boolean =
        (detail.signType == ChaoxingSignType.NORMAL || detail.signType == ChaoxingSignType.SIGN_OUT) &&
            !detail.requiresPhoto

    override suspend fun execute(
        account: SignAccountIdentity,
        detail: ChaoxingActiveDetail,
        request: SignExecutionRequest,
    ): SignExecutionResult {
        remoteDataSource.preSign(
            accountId = account.accountId,
            courseId = request.courseId,
            clazzId = request.clazzId,
            activeId = detail.activeId,
            uid = account.uid,
        )
        return submitAndMap(
            remoteDataSource = remoteDataSource,
            account = account,
            params = StuSignSubmitParams(
                activeId = detail.activeId,
                uid = account.uid,
                fid = account.fid,
                displayName = account.displayName,
                validate = request.captchaValidate,
            ),
        )
    }
}

/**
 * 拍照签到（含要求拍照的签退活动 `activeType=74, ifphoto=1`）策略处理器。
 * 流程：获取云盘 `_token` -> 上传图片取得 `objectId` -> 预加载 -> 提交 `stuSignajax`。
 */
class PhotoSignActionHandler @Inject constructor(private val remoteDataSource: ChaoxingRemoteDataSource) :
    SignActionHandler {
    override val supportedTypes: Set<ChaoxingSignType> = setOf(
        ChaoxingSignType.PHOTO,
        ChaoxingSignType.SIGN_OUT,
    )

    override fun canHandle(detail: ChaoxingActiveDetail): Boolean = detail.requiresPhoto

    override suspend fun execute(
        account: SignAccountIdentity,
        detail: ChaoxingActiveDetail,
        request: SignExecutionRequest,
    ): SignExecutionResult {
        val objectId = when (val photoRes = resolvePhotoObjectId(account, request)) {
            is AppResult.Success -> photoRes.data
            is AppResult.Failure -> return photoRes.error.toSignFailure()
        } ?: return SignExecutionResult.RequiresExtraInput(
            missingField = SignMissingField.PHOTO_IMAGE,
            message = "该签到需要上传现场照片",
        )

        val missingLoc = ensureOptionalLocationPresent(detail, request, "该签到开启了位置范围校验，请先选择签到地点")
        if (missingLoc != null) {
            return missingLoc
        }

        remoteDataSource.preSign(
            accountId = account.accountId,
            courseId = request.courseId,
            clazzId = request.clazzId,
            activeId = detail.activeId,
            uid = account.uid,
        )

        return submitAndMap(
            remoteDataSource = remoteDataSource,
            account = account,
            params = StuSignSubmitParams(
                activeId = detail.activeId,
                uid = account.uid,
                fid = account.fid,
                displayName = account.displayName,
                objectId = objectId,
                validate = request.captchaValidate,
            ).withOptionalLocation(detail, request),
        )
    }

    private suspend fun resolvePhotoObjectId(
        account: SignAccountIdentity,
        request: SignExecutionRequest,
    ): AppResult<String?> {
        val preUploaded = request.preUploadedObjectId?.takeIf { it.isNotBlank() }
        if (preUploaded != null) {
            return AppResult.Success(preUploaded)
        }
        val photoBytes = request.photoBytes
        if (photoBytes == null || photoBytes.isEmpty()) {
            return AppResult.Success(null)
        }
        val token = when (val tokenRes = remoteDataSource.requestPanUploadToken(account.accountId)) {
            is AppResult.Success -> tokenRes.data
            is AppResult.Failure -> return tokenRes
        }
        return remoteDataSource.uploadSignPhoto(
            accountId = account.accountId,
            uid = account.uid,
            token = token,
            fileName = request.photoFileName.orEmpty().ifBlank { "sign_photo.jpg" },
            imageBytes = photoBytes,
        )
    }
}

/**
 * 二维码扫码签到策略处理器（对接 `:core:scanner` 扫描结果或跳转 URL）。
 */
class QrCodeSignActionHandler @Inject constructor(private val remoteDataSource: ChaoxingRemoteDataSource) :
    SignActionHandler {
    override val supportedTypes: Set<ChaoxingSignType> = setOf(ChaoxingSignType.QR_CODE)

    override suspend fun execute(
        account: SignAccountIdentity,
        detail: ChaoxingActiveDetail,
        request: SignExecutionRequest,
    ): SignExecutionResult {
        val resolvedEnc = when (val encRes = resolveQrEnc(account.accountId, request)) {
            is AppResult.Success -> encRes.data
            is AppResult.Failure -> return encRes.error.toSignFailure()
        } ?: return SignExecutionResult.RequiresExtraInput(
            missingField = SignMissingField.QR_CODE_ENC,
            message = "请先扫描课堂展示的签到二维码",
        )

        val missingLoc = ensureOptionalLocationPresent(detail, request, "该二维码签到要求位置范围校验，请先选择签到地点")
        if (missingLoc != null) {
            return missingLoc
        }

        remoteDataSource.preSign(
            accountId = account.accountId,
            courseId = request.courseId,
            clazzId = request.clazzId,
            activeId = detail.activeId,
            uid = account.uid,
        )

        return submitAndMap(
            remoteDataSource = remoteDataSource,
            account = account,
            params = StuSignSubmitParams(
                activeId = detail.activeId,
                uid = account.uid,
                fid = account.fid,
                displayName = account.displayName,
                enc = resolvedEnc,
                validate = request.captchaValidate,
            ).withOptionalLocation(detail, request),
        )
    }

    private suspend fun resolveQrEnc(accountId: String, request: SignExecutionRequest): AppResult<String?> {
        val directEnc = request.enc?.takeIf { it.isNotBlank() }
        if (directEnc != null) {
            return AppResult.Success(directEnc)
        }
        val rawQr = request.rawQrContent?.takeIf { it.isNotBlank() } ?: return AppResult.Success(null)
        val directParsed = ChaoxingQrParser.parse(rawQr)?.enc
        if (directParsed != null) {
            return AppResult.Success(directParsed)
        }
        return when (val redirectRes = remoteDataSource.resolveQrPayload(accountId, rawQr)) {
            is AppResult.Success -> AppResult.Success(redirectRes.data.enc)
            is AppResult.Failure -> redirectRes
        }
    }
}

/**
 * 指定位置签到策略处理器。
 */
class LocationSignActionHandler @Inject constructor(private val remoteDataSource: ChaoxingRemoteDataSource) :
    SignActionHandler {
    override val supportedTypes: Set<ChaoxingSignType> = setOf(ChaoxingSignType.LOCATION)

    override suspend fun execute(
        account: SignAccountIdentity,
        detail: ChaoxingActiveDetail,
        request: SignExecutionRequest,
    ): SignExecutionResult {
        val lat = request.latitude
        val lng = request.longitude
        val validationError = validateSignCoordinates(
            latitude = lat,
            longitude = lng,
            missingMessage = "请先确认或选取当前签到经纬度坐标",
        )
        if (validationError != null) {
            return validationError
        }

        remoteDataSource.preSign(
            accountId = account.accountId,
            courseId = request.courseId,
            clazzId = request.clazzId,
            activeId = detail.activeId,
            uid = account.uid,
        )

        return submitAndMap(
            remoteDataSource = remoteDataSource,
            account = account,
            params = StuSignSubmitParams(
                activeId = detail.activeId,
                uid = account.uid,
                fid = account.fid,
                displayName = account.displayName,
                latitude = lat.toString(),
                longitude = lng.toString(),
                address = request.address ?: detail.locationText.orEmpty(),
                ifTiJiao = "1",
                validate = request.captchaValidate,
            ),
        )
    }
}

/**
 * 手势签到与数字签到码策略处理器。
 */
class CodeAndGestureSignActionHandler @Inject constructor(private val remoteDataSource: ChaoxingRemoteDataSource) :
    SignActionHandler {
    override val supportedTypes: Set<ChaoxingSignType> = setOf(
        ChaoxingSignType.GESTURE,
        ChaoxingSignType.SIGN_CODE,
    )

    override suspend fun execute(
        account: SignAccountIdentity,
        detail: ChaoxingActiveDetail,
        request: SignExecutionRequest,
    ): SignExecutionResult {
        val code = request.signCode?.trim().orEmpty()
        if (code.isEmpty()) {
            return SignExecutionResult.RequiresExtraInput(
                missingField = SignMissingField.SIGN_CODE,
                message = if (detail.signType == ChaoxingSignType.GESTURE) {
                    "请输入手势轨迹序号（如 1235789）"
                } else {
                    "请输入教师公布的签到码"
                },
            )
        }

        when (val checkRes = remoteDataSource.checkSignCode(account.accountId, detail.activeId, code)) {
            is AppResult.Success -> if (!checkRes.data) {
                return SignExecutionResult.Rejected("手势或签到码校验未通过")
            }

            is AppResult.Failure -> return checkRes.error.toSignFailure()
        }

        remoteDataSource.preSign(
            accountId = account.accountId,
            courseId = request.courseId,
            clazzId = request.clazzId,
            activeId = detail.activeId,
            uid = account.uid,
        )

        return submitAndMap(
            remoteDataSource = remoteDataSource,
            account = account,
            params = StuSignSubmitParams(
                activeId = detail.activeId,
                uid = account.uid,
                fid = account.fid,
                displayName = account.displayName,
                signCode = code,
                validate = request.captchaValidate,
            ),
        )
    }
}

/**
 * 插件化签到策略统一调度器：负责前置详情拉取、行为验证码挂起拦截与策略匹配执行。
 */
class SignStrategyDispatcher @Inject constructor(
    private val remoteDataSource: ChaoxingRemoteDataSource,
    private val handlers: Set<@JvmSuppressWildcards SignActionHandler>,
) {
    /** 执行单账号签到：拉取最新配置 -> 核查状态与验证码门禁 -> 路由到对应策略处理器。 */
    suspend fun dispatch(
        account: SignAccountIdentity,
        request: SignExecutionRequest,
        prefetchedDetail: ChaoxingActiveDetail? = null,
    ): SignExecutionResult {
        val detail = prefetchedDetail ?: when (
            val detailRes = remoteDataSource.fetchActiveDetail(account.accountId, request.activeId)
        ) {
            is AppResult.Success -> detailRes.data
            is AppResult.Failure -> return detailRes.error.toSignFailure()
        }

        val preconditionRejection = validateDetailPreconditions(detail, request)
        if (preconditionRejection != null) {
            return preconditionRejection
        }

        val handler = handlers.firstOrNull { it.canHandle(detail) }
            ?: return SignExecutionResult.Failed(
                DomainError.Unsupported("暂不支持该活动类型 (${detail.signType})"),
            )

        return handler.execute(account, detail, request)
    }

    private fun validateDetailPreconditions(
        detail: ChaoxingActiveDetail,
        request: SignExecutionRequest,
    ): SignExecutionResult? = when {
        detail.activityStatus == ChaoxingActivityStatus.ENDED ->
            SignExecutionResult.Rejected("签到活动已结束")
        detail.activityStatus == ChaoxingActivityStatus.NOT_STARTED ->
            SignExecutionResult.Rejected("签到活动尚未开始")
        detail.requiresCaptcha && request.captchaValidate.isNullOrBlank() ->
            SignExecutionResult.RequiresCaptcha(
                captchaId = ChaoxingEndpointConfig.DEFAULT_CAPTCHA_ID,
                activeId = detail.activeId,
                message = "服务端触发行为验证码校验，请完成验证后继续",
            )
        detail.requireFace || detail.requireWeChat ->
            SignExecutionResult.Rejected("该活动开启了人脸或微信专属核验，暂不支持自动代签")
        else -> null
    }
}

private fun ensureOptionalLocationPresent(
    detail: ChaoxingActiveDetail,
    request: SignExecutionRequest,
    missingMessage: String,
): SignExecutionResult? = if (detail.requiresLocation) {
    validateSignCoordinates(
        latitude = request.latitude,
        longitude = request.longitude,
        missingMessage = missingMessage,
    )
} else {
    null
}

private fun validateSignCoordinates(
    latitude: Double?,
    longitude: Double?,
    missingMessage: String,
): SignExecutionResult? = when {
    latitude == null || longitude == null -> SignExecutionResult.RequiresExtraInput(
        missingField = SignMissingField.LOCATION_COORDINATE,
        message = missingMessage,
    )

    !CoordinateConverter.isValidTargetCoordinate(latitude, longitude) -> SignExecutionResult.RequiresExtraInput(
        missingField = SignMissingField.LOCATION_COORDINATE,
        message = "当前经纬度超出有效范围（纬度 -90~90，经度 -180~180），请检查或重新选点",
    )

    else -> null
}

private fun StuSignSubmitParams.withOptionalLocation(
    detail: ChaoxingActiveDetail,
    request: SignExecutionRequest,
): StuSignSubmitParams {
    val lat = request.latitude
    val lng = request.longitude
    return if (CoordinateConverter.isValidTargetCoordinate(lat, lng)) {
        copy(
            latitude = lat.toString(),
            longitude = lng.toString(),
            address = request.address ?: detail.locationText.orEmpty(),
            ifTiJiao = "1",
        )
    } else {
        this
    }
}

private suspend fun submitAndMap(
    remoteDataSource: ChaoxingRemoteDataSource,
    account: SignAccountIdentity,
    params: StuSignSubmitParams,
): SignExecutionResult {
    val enriched = params.copy(
        deviceCode = params.deviceCode ?: account.deviceCode.takeIf { it.isNotBlank() },
        clientUserAgent = params.clientUserAgent ?: account.clientUserAgent?.takeIf { it.isNotBlank() },
    )
    return when (val res = remoteDataSource.submitStuSignAjax(account.accountId, enriched)) {
        is AppResult.Success -> res.data.toExecutionResult(enriched.activeId)
        is AppResult.Failure -> res.error.toSignFailure()
    }
}

private fun DomainError.toSignFailure(): SignExecutionResult = when (this) {
    is DomainError.Unauthorized -> SignExecutionResult.SessionExpired(message)
    else -> SignExecutionResult.Failed(this)
}
