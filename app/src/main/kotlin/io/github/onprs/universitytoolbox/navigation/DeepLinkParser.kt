package io.github.onprs.universitytoolbox.navigation

import io.github.onprs.universitytoolbox.feature.unlock.domain.UnlockDeepLinkParser
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 宿主深链解析结果，区分授权激活入口与内部工具直达入口。
 */
sealed interface DeepLinkTarget {
    data class Activation(val activationCode: String?) : DeepLinkTarget

    data class Tool(val toolId: String) : DeepLinkTarget
}

/**
 * 纯 Kotlin 深链解析器，将外部唤起 URI 归一为授权激活或工具路由请求。
 */
object DeepLinkParser {
    private const val SCHEME_TOOLBOX = "universitytoolbox"
    private const val HOST_TOOL = "tool"
    private val toolIdQueryKeys = listOf("id", "toolId", "tool")

    fun parse(rawUri: String?): DeepLinkTarget? {
        val trimmed = rawUri?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null
        }

        if (UnlockDeepLinkParser.isActivationDeepLink(trimmed)) {
            return DeepLinkTarget.Activation(
                activationCode = UnlockDeepLinkParser.extractActivationCode(trimmed),
            )
        }

        val parsedUri = runCatching { URI(trimmed) }.getOrNull()
        val isToolDeepLink = parsedUri != null &&
            parsedUri.scheme?.equals(SCHEME_TOOLBOX, ignoreCase = true) == true &&
            parsedUri.host?.equals(HOST_TOOL, ignoreCase = true) == true
        if (!isToolDeepLink) {
            return null
        }

        val pathToolId = parsedUri.rawPath
            .orEmpty()
            .trim('/')
            .split('/')
            .firstOrNull { it.isNotEmpty() }
            ?.let(::decodeComponent)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val toolId = pathToolId ?: extractToolIdFromQuery(parsedUri.rawQuery)
        return toolId?.let { DeepLinkTarget.Tool(it) }
    }

    private fun extractToolIdFromQuery(rawQuery: String?): String? {
        if (rawQuery.isNullOrBlank()) {
            return null
        }
        val params = rawQuery.split('&').mapNotNull { segment ->
            if (segment.isEmpty()) return@mapNotNull null
            val key = decodeComponent(segment.substringBefore('='))
            val value = decodeComponent(segment.substringAfter('=', ""))
            key to value
        }
        for (preferredKey in toolIdQueryKeys) {
            val matched = params.firstOrNull { (k, v) -> k == preferredKey && v.isNotBlank() }
            if (matched != null) {
                return matched.second.trim()
            }
        }
        return null
    }

    private fun decodeComponent(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
