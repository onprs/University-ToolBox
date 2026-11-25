package io.github.onprs.universitytoolbox.core.common.result

/**
 * 跨模块统一领域错误模型，避免将底层实现异常直接暴露给 UI 层。
 */
sealed interface DomainError {
    val message: String
    val cause: Throwable?
        get() = null

    /** 网络通信或 HTTP 协议错误。 */
    data class Network(
        override val message: String,
        val httpCode: Int? = null,
        override val cause: Throwable? = null,
    ) : DomainError

    /** 会话过期、未认证或服务端拒绝访问。 */
    data class Unauthorized(override val message: String, override val cause: Throwable? = null) : DomainError

    /** 输入参数或格式校验失败。 */
    data class Validation(override val message: String, val field: String? = null) : DomainError

    /** 本地数据库或偏好存储读写异常。 */
    data class Storage(override val message: String, override val cause: Throwable? = null) : DomainError

    /** 加解密、签名验证或安全环境异常。 */
    data class Security(override val message: String, override val cause: Throwable? = null) : DomainError

    /** 触发限流或频控拦截。 */
    data class RateLimited(override val message: String, val retryAfterMs: Long? = null) : DomainError

    /** 请求的目标资源不存在。 */
    data class NotFound(override val message: String) : DomainError

    /** 当前环境或配置不支持该操作。 */
    data class Unsupported(override val message: String) : DomainError

    /** 未归类的兜底错误。 */
    data class Unknown(override val message: String, override val cause: Throwable? = null) : DomainError
}
