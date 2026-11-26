package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 超星宽容 JSON AST 工具与全端点防崩溃提取器。
 *
 * 超星服务端在活动未开始、进行中、已结束或异常分支下存在大量动态类型漂移：
 * - 时间戳字段（如 `endTime`、`startTime`）在未设定时返回空字符串 `""`；
 * - 整数字段（如 `otherId`、`status`、`cpi`）在不同接口间交替返回数字、数字字符串或空串；
 * - `data`、`msg`、`content` 在报错或空目录时可能由对象退化为空字符串 `""` 或空数组 `[]`。
 *
 * 本层统一基于语法树逐字段安全读取，并对列表元素做单条异常隔离，杜绝单字段类型漂移导致整页崩溃。
 */
object ChaoxingSafeJson {
    val codec: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun parseObjectOrNull(raw: String): JsonObject? {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            return null
        }
        return runCatching { codec.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
    }

    fun looksLikeLoginHtml(raw: String): Boolean {
        val trimmed = raw.trimStart().removePrefix("\uFEFF")
        if (!trimmed.startsWith("<")) {
            return false
        }
        val head = trimmed.take(HTML_INSPECT_LIMIT)
        val hasPassportMarker = head.contains("passport2.chaoxing.com", ignoreCase = true) ||
            head.contains("/mlogin", ignoreCase = true) ||
            head.contains("fanyalogin", ignoreCase = true)
        return hasPassportMarker || head.contains("请登录") || head.contains("用户登录")
    }

    private const val HTML_INSPECT_LIMIT = 2048
}

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonObject.optElement(vararg keys: String): JsonElement? =
    keys.firstNotNullOfOrNull { key -> this[key]?.takeUnless { it is JsonNull } }

internal fun JsonObject.optObject(vararg keys: String): JsonObject? = optElement(*keys).asObjectOrNull()

internal fun JsonObject.optArray(vararg keys: String): JsonArray? = optElement(*keys).asArrayOrNull()

internal fun JsonObject.optString(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    val primitive = this[key] as? JsonPrimitive
    val content = if (primitive == null || primitive is JsonNull) null else primitive.contentOrNull
    content?.takeUnless { !primitive!!.isString && it.equals("null", ignoreCase = true) }
}

internal fun JsonObject.optNonBlankString(vararg keys: String): String? {
    val raw = optString(*keys)?.trim() ?: return null
    return raw.takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
}

internal fun JsonObject.optLong(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
    val primitive = this[key] as? JsonPrimitive
    if (primitive == null || primitive is JsonNull) null else parsePrimitiveLong(primitive)
}

internal fun JsonObject.optInt(vararg keys: String): Int? = optLong(*keys)?.toInt()

internal fun JsonObject.optDouble(vararg keys: String): Double? = keys.firstNotNullOfOrNull { key ->
    val primitive = this[key] as? JsonPrimitive
    if (primitive == null || primitive is JsonNull) null else parsePrimitiveDouble(primitive)
}

internal fun JsonObject.optBoolean(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
    val primitive = this[key] as? JsonPrimitive
    if (primitive == null || primitive is JsonNull) null else parsePrimitiveBoolean(primitive)
}

internal inline fun <T : Any> JsonArray?.mapObjectsSafely(transform: (JsonObject) -> T?): List<T> {
    if (this == null || isEmpty()) {
        return emptyList()
    }
    val destination = ArrayList<T>(size)
    for (element in this) {
        val obj = element as? JsonObject
        val mapped = if (obj != null) runCatching { transform(obj) }.getOrNull() else null
        if (mapped != null) {
            destination += mapped
        }
    }
    return destination
}

private fun parsePrimitiveLong(primitive: JsonPrimitive): Long? {
    val direct = primitive.longOrNull ?: primitive.booleanOrNull?.let { if (it) 1L else 0L }
    if (direct != null) return direct
    val text = primitive.contentOrNull?.trim().orEmpty()
    if (text.isEmpty() || text.equals("null", ignoreCase = true)) return null
    return text.toLongOrNull() ?: text.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
}

private fun parsePrimitiveDouble(primitive: JsonPrimitive): Double? {
    val direct = primitive.doubleOrNull?.takeIf { it.isFinite() }
    if (direct != null) return direct
    val text = primitive.contentOrNull?.trim().orEmpty()
    if (text.isEmpty() || text.equals("null", ignoreCase = true)) return null
    return text.toDoubleOrNull()?.takeIf { it.isFinite() }
}

private fun parsePrimitiveBoolean(primitive: JsonPrimitive): Boolean? {
    val direct = primitive.booleanOrNull
    if (direct != null) return direct
    val text = primitive.contentOrNull?.trim().orEmpty()
    return when {
        text.equals("true", ignoreCase = true) || text == "1" -> true
        text.equals("false", ignoreCase = true) || text == "0" -> false
        else -> null
    }
}
