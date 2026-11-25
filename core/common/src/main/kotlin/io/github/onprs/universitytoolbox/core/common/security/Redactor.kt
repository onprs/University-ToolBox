package io.github.onprs.universitytoolbox.core.common.security

/**
 * 敏感信息脱敏工具，用于日志拦截器、诊断导出与脱敏展示。
 */
object Redactor {
    private const val MASK_CHAR = '*'
    private const val REDACTED_PLACEHOLDER = "<REDACTED>"
    private const val PHONE_MIN_LENGTH = 7
    private const val PHONE_PREFIX_KEEP = 3
    private const val PHONE_SUFFIX_KEEP = 4
    private const val DEFAULT_SECRET_PREFIX = 4
    private const val DEFAULT_SECRET_SUFFIX = 4

    private val phoneRegex = Regex("""(?<!\d)(1[3-9]\d)(\d{4})(\d{4})(?!\d)""")
    private val headerSecretRegex = Regex(
        pattern = """(?i)\b(Cookie|Set-Cookie|Authorization|X-Auth-Token)\s*[:=]\s*([^\r\n]+)""",
    )
    private val keyValueSecretRegex = Regex(
        pattern = """(?i)\b(password|passwd|pwd|uname|token|vc3|_d|uf|UID|_uid|rawKey|licenseKey)""" +
            """\s*([=:])\s*([^\s&,;]+)""",
    )
    private val quotedSecretRegex = Regex(
        pattern = """(?i)"(password|passwd|pwd|uname|token|vc3|_d|uf|UID|_uid|rawKey|licenseKey)"\s*:\s*"([^"]*)"""",
    )

    /**
     * 对手机号进行掩码处理，仅保留前 3 位与后 4 位。
     */
    fun maskPhone(phone: String): String {
        val trimmed = phone.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        if (trimmed.length < PHONE_MIN_LENGTH) {
            return MASK_CHAR.toString().repeat(trimmed.length)
        }
        val middleLength = trimmed.length - PHONE_PREFIX_KEEP - PHONE_SUFFIX_KEEP
        return buildString(trimmed.length) {
            append(trimmed.substring(0, PHONE_PREFIX_KEEP))
            repeat(middleLength) { append(MASK_CHAR) }
            append(trimmed.substring(trimmed.length - PHONE_SUFFIX_KEEP))
        }
    }

    /**
     * 对激活码或密钥编号等长凭据字符串做首尾保留、中间掩码处理。
     */
    fun maskSecret(
        secret: String,
        visiblePrefix: Int = DEFAULT_SECRET_PREFIX,
        visibleSuffix: Int = DEFAULT_SECRET_SUFFIX,
    ): String {
        val trimmed = secret.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        val safePrefix = visiblePrefix.coerceAtLeast(0)
        val safeSuffix = visibleSuffix.coerceAtLeast(0)
        if (trimmed.length <= safePrefix + safeSuffix) {
            return MASK_CHAR.toString().repeat(trimmed.length)
        }
        val middleLength = trimmed.length - safePrefix - safeSuffix
        return buildString(trimmed.length) {
            append(trimmed.substring(0, safePrefix))
            repeat(middleLength) { append(MASK_CHAR) }
            append(trimmed.substring(trimmed.length - safeSuffix))
        }
    }

    /**
     * 清理文本或日志片段中的敏感字段（HTTP 认证头、Cookie、密码字段与手机号）。
     */
    fun redactText(input: String): String {
        if (input.isEmpty()) {
            return input
        }
        var sanitized = headerSecretRegex.replace(input) { match ->
            "${match.groupValues[1]}: $REDACTED_PLACEHOLDER"
        }
        sanitized = quotedSecretRegex.replace(sanitized) { match ->
            "\"${match.groupValues[1]}\": \"$REDACTED_PLACEHOLDER\""
        }
        sanitized = keyValueSecretRegex.replace(sanitized) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}$REDACTED_PLACEHOLDER"
        }
        sanitized = phoneRegex.replace(sanitized) { match ->
            "${match.groupValues[1]}****${match.groupValues[3]}"
        }
        return sanitized
    }
}
