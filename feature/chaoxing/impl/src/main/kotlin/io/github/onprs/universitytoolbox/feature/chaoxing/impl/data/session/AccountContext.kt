package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session

/**
 * OkHttp Request Tag，用于在单次 HTTP 请求链路中显式绑定目标账号分区，防止多账号 Cookie 串号。
 */
data class AccountContext(val accountId: String) {
    init {
        require(accountId.isNotBlank()) { "AccountContext accountId must not be blank" }
    }
}
