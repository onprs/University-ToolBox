package io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.domain

/**
 * 条码与二维码载荷的语义分类。
 */
enum class QrPayloadCategory {
    URL,
    WIFI,
    CONTACT,
    EMAIL,
    PHONE,
    SMS,
    GEO,
    JSON,
    CAMPUS_SIGN,
    PLAIN_TEXT,
}

/**
 * 载荷安全检视提示等级。
 */
enum class SecurityNoticeKind {
    SAFE_HTTPS,
    INSECURE_HTTP,
    IP_HOST_DETECTED,
    SENSITIVE_QUERY_PARAM,
    CREDENTIAL_PROTECTED,
    RAW_PAYLOAD_EMPTY,
}

/**
 * 结构化解析字段语义键。
 */
enum class QrFieldKey {
    FORMAT,
    RAW_LENGTH,
    SCHEME,
    HOST,
    PORT,
    PATH,
    QUERY_PARAM,
    WIFI_SSID,
    WIFI_ENCRYPTION,
    WIFI_PASSWORD,
    WIFI_HIDDEN,
    CONTACT_NAME,
    CONTACT_PHONE,
    CONTACT_EMAIL,
    CONTACT_ORG,
    EMAIL_ADDRESS,
    EMAIL_SUBJECT,
    PHONE_NUMBER,
    SMS_RECIPIENT,
    SMS_BODY,
    LATITUDE,
    LONGITUDE,
    JSON_KEYS,
    CAMPUS_ACTION,
    TEXT_PREVIEW,
}

/**
 * 解析后的单个结构化字段。
 */
data class ParsedField(
    val key: QrFieldKey,
    val value: String,
    val isSensitive: Boolean = false,
    val customLabel: String? = null,
)

/**
 * 单次条码/二维码检视完整结果。
 */
data class InspectedBarcodePayload(
    val id: String,
    val rawContent: String,
    val formatLabel: String,
    val category: QrPayloadCategory,
    val summaryTitle: String,
    val fields: List<ParsedField>,
    val securityNotices: List<SecurityNoticeKind>,
    val scannedAtEpochMs: Long,
)
