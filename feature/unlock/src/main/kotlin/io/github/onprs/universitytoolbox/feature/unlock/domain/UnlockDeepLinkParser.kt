package io.github.onprs.universitytoolbox.feature.unlock.domain

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 激活口令与 Deep Link 解析器。
 * 支持直接密钥字符串、常见 `universitytoolbox://` 深链或含 `key`/`code`/`token` 参数的 URL。
 */
object UnlockDeepLinkParser {
    private val supportedSchemes = setOf("universitytoolbox", "https", "http")
    private val supportedHostsOrPaths = setOf("unlock", "activate", "license")
    private val supportedQueryKeys = listOf("key", "code", "license", "token", "activation")

    /**
     * 从用户输入或外部传入的 URI 字符串中提取干净的激活码文本；无法提取有效内容时返回 null。
     */
    fun extractActivationCode(rawInput: String?): String? {
        val trimmed = rawInput?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null
        }

        val parsedUri = runCatching { URI(trimmed) }.getOrNull()
        val scheme = parsedUri?.scheme?.lowercase()
        val rawCandidate = if (parsedUri != null && scheme in supportedSchemes) {
            extractFromSupportedUri(parsedUri)
        } else {
            trimmed
        }
        return rawCandidate?.let(::normalizeCodeText)
    }

    /**
     * 判断文本是否为支持的激活深链格式。
     */
    fun isActivationDeepLink(rawInput: String?): Boolean {
        val trimmed = rawInput?.trim().orEmpty()
        val uri = trimmed.takeIf { it.isNotEmpty() }?.let { runCatching { URI(it) }.getOrNull() }
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || scheme !in supportedSchemes) {
            return false
        }
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.rawPath?.lowercase().orEmpty()
        return host in supportedHostsOrPaths || supportedHostsOrPaths.any { path.contains(it) }
    }

    private fun extractFromSupportedUri(uri: URI): String? {
        val fromQuery = extractFromUriQuery(uri.rawQuery)
        if (!fromQuery.isNullOrEmpty()) {
            return fromQuery
        }
        val host = uri.host?.lowercase().orEmpty()
        val pathSegments = uri.rawPath.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
        return if (host in supportedHostsOrPaths && pathSegments.size == 1) {
            decodeUrlComponent(pathSegments.first())
        } else {
            null
        }
    }

    private fun extractFromUriQuery(rawQuery: String?): String? {
        if (rawQuery.isNullOrBlank()) {
            return null
        }
        val params = rawQuery.split('&').mapNotNull { segment ->
            if (segment.isEmpty()) return@mapNotNull null
            val key = decodeUrlComponent(segment.substringBefore('=')).lowercase()
            val value = decodeUrlComponent(segment.substringAfter('=', ""))
            key to value
        }
        for (preferredKey in supportedQueryKeys) {
            val match = params.firstOrNull { (k, v) -> k == preferredKey && v.isNotBlank() }
            if (match != null) {
                return match.second
            }
        }
        return null
    }

    private fun normalizeCodeText(input: String): String? {
        val compact = input.replace(Regex("""[\r\n\t ]+"""), "")
        return compact.takeIf { it.isNotEmpty() }
    }

    private fun decodeUrlComponent(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
