package io.github.onprs.universitytoolbox.feature.home.domain

import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor
import java.util.Locale

/**
 * 工具即时检索匹配器：同时支持工具 ID、本地化标题/描述、关键词及汉字拼音首字母缩写匹配。
 */
object ToolSearchMatcher {
    /**
     * 计算当前工具与查询词的匹配得分；返回 0 表示不匹配，分值越高相关性越高。
     */
    fun matchScore(tool: ToolDescriptor, rawQuery: String, localizedTitle: String, localizedDescription: String): Int {
        val query = rawQuery.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) {
            return 1
        }
        val lowerTitle = localizedTitle.lowercase(Locale.ROOT)
        val lowerDesc = localizedDescription.lowercase(Locale.ROOT)
        val lowerId = tool.id.lowercase(Locale.ROOT)

        return when {
            lowerTitle.startsWith(query) -> SCORE_EXACT_PREFIX
            lowerTitle.contains(query) -> SCORE_TITLE_CONTAINS
            PinyinInitials.extractInitials(localizedTitle).startsWith(query) -> SCORE_PINYIN_PREFIX
            PinyinInitials.matches(localizedTitle, query) -> SCORE_PINYIN_CONTAINS
            tool.searchKeywords.any { it.lowercase(Locale.ROOT).contains(query) } -> SCORE_KEYWORD_CONTAINS
            lowerId.contains(query) -> SCORE_ID_CONTAINS
            PinyinInitials.matches(localizedDescription, query) || lowerDesc.contains(query) -> SCORE_DESC_CONTAINS
            else -> 0
        }
    }

    private const val SCORE_EXACT_PREFIX = 100
    private const val SCORE_TITLE_CONTAINS = 80
    private const val SCORE_PINYIN_PREFIX = 70
    private const val SCORE_PINYIN_CONTAINS = 60
    private const val SCORE_KEYWORD_CONTAINS = 50
    private const val SCORE_ID_CONTAINS = 40
    private const val SCORE_DESC_CONTAINS = 20
}
