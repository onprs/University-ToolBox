package io.github.onprs.universitytoolbox.feature.home.domain

import java.nio.charset.Charset
import java.text.Collator
import java.util.Locale

/**
 * 汉语拼音首字母提取工具，用于支持中文工具名称的首字母缩写即时检索。
 */
object PinyinInitials {
    private const val GB2312_LEVEL1_START = 45217
    private const val GB2312_LEVEL1_END = 55289
    private const val ASCII_UPPER_BOUND = 128
    private const val BYTE_MASK = 0xFF
    private const val BYTE_SHIFT = 8

    private val gbkCharset: Charset = Charset.forName("GBK")

    // GB2312 一级汉字（按汉语拼音排序）各声母起始编码点：啊、芭、擦、搭...匝
    private val level1Boundaries = intArrayOf(
        45217,
        45253,
        45761,
        46318,
        46826,
        47010,
        47297,
        47614,
        48119,
        49062,
        49324,
        49896,
        50371,
        50614,
        50622,
        50906,
        51387,
        51446,
        52218,
        52698,
        52980,
        53689,
        54481,
    )

    private val initialLetters = charArrayOf(
        'a',
        'b',
        'c',
        'd',
        'e',
        'f',
        'g',
        'h',
        'j',
        'k',
        'l',
        'm',
        'n',
        'o',
        'p',
        'q',
        'r',
        's',
        't',
        'w',
        'x',
        'y',
        'z',
    )

    private val anchorStrings = arrayOf(
        "啊",
        "芭",
        "擦",
        "搭",
        "蛾",
        "发",
        "噶",
        "哈",
        "击",
        "喀",
        "垃",
        "妈",
        "拿",
        "哦",
        "啪",
        "期",
        "然",
        "撒",
        "塌",
        "挖",
        "昔",
        "压",
        "匝",
    )

    private val collator: Collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE).apply {
        strength = Collator.PRIMARY
    }

    /**
     * 提取输入字符串中各字符对应的检索首字母序列（英文与数字保留小写，中文提取拼音首字母）。
     */
    fun extractInitials(input: String): String {
        if (input.isEmpty()) {
            return ""
        }
        val builder = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch.isLetterOrDigit() && ch.code < ASCII_UPPER_BOUND -> builder.append(ch.lowercaseChar())
                isCjkCharacter(ch) -> {
                    initialForChineseChar(ch)?.let { builder.append(it) }
                }
            }
        }
        return builder.toString()
    }

    /**
     * 判断 [text]（原文或拼音首字母缩写）是否匹配查询词 [normalizedQuery]。
     */
    fun matches(text: String, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) {
            return true
        }
        if (text.lowercase(Locale.ROOT).contains(normalizedQuery)) {
            return true
        }
        val compactQuery = normalizedQuery.filter { it.isLetterOrDigit() }
        if (compactQuery.isEmpty()) {
            return false
        }
        val initials = extractInitials(text)
        return initials.contains(compactQuery)
    }

    private fun initialForChineseChar(ch: Char): Char? {
        val level1 = lookupLevel1Initial(ch)
        if (level1 != null) {
            return level1
        }
        val singleCharString = ch.toString()
        val matchedIndex = anchorStrings.indices.reversed().firstOrNull { index ->
            collator.compare(singleCharString, anchorStrings[index]) >= 0
        }
        return matchedIndex?.let { initialLetters[it] }
    }

    private fun lookupLevel1Initial(ch: Char): Char? {
        val bytes = ch.toString().toByteArray(gbkCharset)
        if (bytes.size != 2) {
            return null
        }
        val code = ((bytes[0].toInt() and BYTE_MASK) shl BYTE_SHIFT) or (bytes[1].toInt() and BYTE_MASK)
        if (code !in GB2312_LEVEL1_START..GB2312_LEVEL1_END) {
            return null
        }
        val index = level1Boundaries.indices.reversed().firstOrNull { code >= level1Boundaries[it] }
        return index?.let { initialLetters[it] }
    }

    private fun isCjkCharacter(ch: Char): Boolean {
        val block = Character.UnicodeBlock.of(ch)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }
}
