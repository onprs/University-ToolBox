package io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.domain

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 条码/二维码载荷结构化与安全解析器（纯 Kotlin 实现）。
 */
object QrContentInspector {
    private const val SUMMARY_MAX_LENGTH = 48
    private const val TEXT_PREVIEW_MAX_LENGTH = 240
    private val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val sensitiveParamKeys = setOf(
        "token",
        "password",
        "passwd",
        "pwd",
        "secret",
        "auth",
        "cookie",
        "vc3",
        "uid",
        "_uid",
        "enc",
        "session",
        "key",
        "licensekey",
    )

    private val jsonParser = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    /**
     * 对条码原始内容进行语义分类、字段提取与安全风险标注。
     */
    fun inspect(
        rawContent: String,
        formatLabel: String = "QR_CODE",
        id: String = "",
        scannedAtEpochMs: Long = 0L,
    ): InspectedBarcodePayload {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return InspectedBarcodePayload(
                id = id,
                rawContent = rawContent,
                formatLabel = formatLabel,
                category = QrPayloadCategory.PLAIN_TEXT,
                summaryTitle = "",
                fields = listOf(
                    ParsedField(QrFieldKey.FORMAT, formatLabel),
                    ParsedField(QrFieldKey.RAW_LENGTH, "0"),
                ),
                securityNotices = listOf(SecurityNoticeKind.RAW_PAYLOAD_EMPTY),
                scannedAtEpochMs = scannedAtEpochMs,
            )
        }

        val baseFields = listOf(
            ParsedField(QrFieldKey.FORMAT, formatLabel),
            ParsedField(QrFieldKey.RAW_LENGTH, rawContent.length.toString()),
        )

        return dispatchInspection(id, rawContent, formatLabel, trimmed, baseFields, scannedAtEpochMs)
    }

    private fun dispatchInspection(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        val prefixRule = prefixInspectors.firstOrNull { (prefix, _) ->
            trimmed.startsWith(prefix, ignoreCase = true)
        }
        return when {
            prefixRule != null ->
                prefixRule.second(id, rawContent, formatLabel, trimmed, baseFields, scannedAtEpochMs)
            looksLikeJson(trimmed) ->
                parseJson(id, rawContent, formatLabel, trimmed, baseFields, scannedAtEpochMs)
            else ->
                parsePlainText(id, rawContent, formatLabel, trimmed, baseFields, scannedAtEpochMs)
        }
    }

    private val prefixInspectors: List<
        Pair<
            String,
            (String, String, String, String, List<ParsedField>, Long) -> InspectedBarcodePayload,
            >,
        > = listOf(
        "WIFI:" to ::parseWifi,
        "BEGIN:VCARD" to ::parseVCard,
        "MECARD:" to ::parseMeCard,
        "SIGNIN:" to ::parseCampusSignToken,
        "http://" to ::parseWebUrl,
        "https://" to ::parseWebUrl,
        "mailto:" to ::parseMailto,
        "tel:" to ::parsePhone,
        "smsto:" to ::parseSms,
        "sms:" to ::parseSms,
        "geo:" to ::parseGeo,
    )

    private fun parseWebUrl(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        val uri = runCatching { URI(trimmed) }.getOrNull()
        val scheme = uri?.scheme?.lowercase().orEmpty()
        val host = uri?.host.orEmpty()
        val port = uri?.port?.takeIf { it > 0 }
        val path = uri?.rawPath?.takeIf { it.isNotBlank() }
        val queryPairs = parseQueryParams(uri?.rawQuery)

        val isCampusSign = host.contains("chaoxing.com", ignoreCase = true) &&
            (path.orEmpty().contains("sign", ignoreCase = true) || queryPairs.any { it.first == "enc" })
        val category = if (isCampusSign) QrPayloadCategory.CAMPUS_SIGN else QrPayloadCategory.URL

        val fields = buildList {
            addAll(baseFields)
            if (scheme.isNotEmpty()) add(ParsedField(QrFieldKey.SCHEME, scheme))
            if (host.isNotEmpty()) add(ParsedField(QrFieldKey.HOST, host))
            if (port != null) add(ParsedField(QrFieldKey.PORT, port.toString()))
            if (path != null) add(ParsedField(QrFieldKey.PATH, path))
            if (isCampusSign) {
                add(ParsedField(QrFieldKey.CAMPUS_ACTION, "attendance_qr"))
            }
            queryPairs.forEach { (key, value) ->
                add(
                    ParsedField(
                        key = QrFieldKey.QUERY_PARAM,
                        value = value,
                        isSensitive = isSensitiveKey(key),
                        customLabel = key,
                    ),
                )
            }
        }

        val notices = buildList {
            if (scheme == "http") {
                add(SecurityNoticeKind.INSECURE_HTTP)
            } else if (scheme == "https") {
                add(SecurityNoticeKind.SAFE_HTTPS)
            }
            if (isIpAddress(host)) {
                add(SecurityNoticeKind.IP_HOST_DETECTED)
            }
            if (queryPairs.any { (key, _) -> isSensitiveKey(key) }) {
                add(SecurityNoticeKind.SENSITIVE_QUERY_PARAM)
            }
        }

        val summary = host.ifEmpty { truncate(trimmed, SUMMARY_MAX_LENGTH) }
        return InspectedBarcodePayload(
            id = id,
            rawContent = rawContent,
            formatLabel = formatLabel,
            category = category,
            summaryTitle = summary,
            fields = fields,
            securityNotices = notices,
            scannedAtEpochMs = scannedAtEpochMs,
        )
    }

    private fun parseWifi(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        val body = trimmed.substringAfter(':', "")
        val tags = parseWifiTags(body)
        val ssid = tags["S"].orEmpty()
        val encryption = tags["T"].orEmpty()
        val password = tags["P"].orEmpty()
        val hidden = tags["H"].orEmpty()

        val fields = buildList {
            addAll(baseFields)
            if (ssid.isNotEmpty()) add(ParsedField(QrFieldKey.WIFI_SSID, ssid))
            if (encryption.isNotEmpty()) add(ParsedField(QrFieldKey.WIFI_ENCRYPTION, encryption))
            if (password.isNotEmpty()) {
                add(ParsedField(QrFieldKey.WIFI_PASSWORD, password, isSensitive = true))
            }
            if (hidden.isNotEmpty()) add(ParsedField(QrFieldKey.WIFI_HIDDEN, hidden))
        }
        val notices = if (password.isNotEmpty()) {
            listOf(SecurityNoticeKind.CREDENTIAL_PROTECTED)
        } else {
            emptyList()
        }

        return InspectedBarcodePayload(
            id = id,
            rawContent = rawContent,
            formatLabel = formatLabel,
            category = QrPayloadCategory.WIFI,
            summaryTitle = ssid.ifEmpty { "Wi-Fi" },
            fields = fields,
            securityNotices = notices,
            scannedAtEpochMs = scannedAtEpochMs,
        )
    }

    private fun parseWifiTags(body: String): Map<String, String> = buildMap {
        splitEscaped(body, ';').forEach { segment ->
            val colonIdx = segment.indexOf(':')
            if (colonIdx > 0) {
                val tag = segment.substring(0, colonIdx).uppercase()
                val value = unescapeWifiValue(segment.substring(colonIdx + 1))
                put(tag, value)
            }
        }
    }

    private fun parseVCard(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        val extracted = extractVCardContact(trimmed)
        val fields = buildContactFields(baseFields, extracted)
        return InspectedBarcodePayload(
            id = id,
            rawContent = rawContent,
            formatLabel = formatLabel,
            category = QrPayloadCategory.CONTACT,
            summaryTitle = extracted.name.ifEmpty { truncate(trimmed, SUMMARY_MAX_LENGTH) },
            fields = fields,
            securityNotices = emptyList(),
            scannedAtEpochMs = scannedAtEpochMs,
        )
    }

    private data class ContactRecord(
        var name: String = "",
        var phone: String = "",
        var email: String = "",
        var org: String = "",
    )

    private fun extractVCardContact(trimmed: String): ContactRecord {
        val record = ContactRecord()
        trimmed.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val upper = line.uppercase()
            val value = line.substringAfter(':', "")
            when {
                upper.startsWith("FN:") -> record.name = value
                upper.startsWith("N:") && record.name.isEmpty() -> {
                    record.name = value.replace(';', ' ').trim()
                }
                upper.startsWith("TEL") && record.phone.isEmpty() -> record.phone = value
                upper.startsWith("EMAIL") && record.email.isEmpty() -> record.email = value
                upper.startsWith("ORG:") && record.org.isEmpty() -> record.org = value
            }
        }
        return record
    }

    private fun buildContactFields(baseFields: List<ParsedField>, record: ContactRecord): List<ParsedField> =
        buildList {
            addAll(baseFields)
            if (record.name.isNotEmpty()) add(ParsedField(QrFieldKey.CONTACT_NAME, record.name))
            if (record.phone.isNotEmpty()) add(ParsedField(QrFieldKey.CONTACT_PHONE, record.phone))
            if (record.email.isNotEmpty()) add(ParsedField(QrFieldKey.CONTACT_EMAIL, record.email))
            if (record.org.isNotEmpty()) add(ParsedField(QrFieldKey.CONTACT_ORG, record.org))
        }

    private fun parseMeCard(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        var name = ""
        var phone = ""
        var email = ""
        var org = ""

        splitEscaped(trimmed.substringAfter(':', ""), ';').forEach { part ->
            val key = part.substringBefore(':').uppercase()
            val value = unescapeWifiValue(part.substringAfter(':', ""))
            when (key) {
                "N" -> name = value
                "TEL" -> if (phone.isEmpty()) phone = value
                "EMAIL" -> if (email.isEmpty()) email = value
                "ORG" -> if (org.isEmpty()) org = value
            }
        }

        val fields = buildList {
            addAll(baseFields)
            if (name.isNotEmpty()) add(ParsedField(QrFieldKey.CONTACT_NAME, name))
            if (phone.isNotEmpty()) add(ParsedField(QrFieldKey.CONTACT_PHONE, phone))
            if (email.isNotEmpty()) add(ParsedField(QrFieldKey.CONTACT_EMAIL, email))
            if (org.isNotEmpty()) add(ParsedField(QrFieldKey.CONTACT_ORG, org))
        }
        return InspectedBarcodePayload(
            id = id,
            rawContent = rawContent,
            formatLabel = formatLabel,
            category = QrPayloadCategory.CONTACT,
            summaryTitle = name.ifEmpty { truncate(trimmed, SUMMARY_MAX_LENGTH) },
            fields = fields,
            securityNotices = emptyList(),
            scannedAtEpochMs = scannedAtEpochMs,
        )
    }

    private fun parseCampusSignToken(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        val body = trimmed.substringAfter(':', "")
        val fields = buildList {
            addAll(baseFields)
            add(ParsedField(QrFieldKey.CAMPUS_ACTION, "attendance_token"))
            body.split('&').forEach { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                if (key.isNotEmpty()) {
                    add(
                        ParsedField(
                            key = QrFieldKey.QUERY_PARAM,
                            value = value,
                            isSensitive = isSensitiveKey(key),
                            customLabel = key,
                        ),
                    )
                }
            }
        }
        val hasSensitive = fields.any { it.isSensitive }
        val notices = if (hasSensitive) listOf(SecurityNoticeKind.SENSITIVE_QUERY_PARAM) else emptyList()
        return InspectedBarcodePayload(
            id = id,
            rawContent = rawContent,
            formatLabel = formatLabel,
            category = QrPayloadCategory.CAMPUS_SIGN,
            summaryTitle = truncate(trimmed, SUMMARY_MAX_LENGTH),
            fields = fields,
            securityNotices = notices,
            scannedAtEpochMs = scannedAtEpochMs,
        )
    }

    private fun parseMailto(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        val body = trimmed.substringAfter(':', "")
        val address = body.substringBefore('?')
        val query = body.substringAfter('?', "")
        val subject = parseQueryParams(query).firstOrNull { it.first.equals("subject", ignoreCase = true) }?.second

        val fields = buildList {
            addAll(baseFields)
            if (address.isNotEmpty()) add(ParsedField(QrFieldKey.EMAIL_ADDRESS, address))
            if (!subject.isNullOrEmpty()) add(ParsedField(QrFieldKey.EMAIL_SUBJECT, subject))
        }
        return InspectedBarcodePayload(
            id = id,
            rawContent = rawContent,
            formatLabel = formatLabel,
            category = QrPayloadCategory.EMAIL,
            summaryTitle = address.ifEmpty { truncate(trimmed, SUMMARY_MAX_LENGTH) },
            fields = fields,
            securityNotices = emptyList(),
            scannedAtEpochMs = scannedAtEpochMs,
        )
    }

    private fun parsePhone(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        val number = trimmed.substringAfter(':', "")
        val fields = baseFields + ParsedField(QrFieldKey.PHONE_NUMBER, number)
        return InspectedBarcodePayload(
            id = id,
            rawContent = rawContent,
            formatLabel = formatLabel,
            category = QrPayloadCategory.PHONE,
            summaryTitle = number.ifEmpty { truncate(trimmed, SUMMARY_MAX_LENGTH) },
            fields = fields,
            securityNotices = emptyList(),
            scannedAtEpochMs = scannedAtEpochMs,
        )
    }

    private fun parseSms(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        val body = trimmed.substringAfter(':', "")
        val recipient = body.substringBefore(':')
        val message = body.substringAfter(':', "")
        val fields = buildList {
            addAll(baseFields)
            if (recipient.isNotEmpty()) add(ParsedField(QrFieldKey.SMS_RECIPIENT, recipient))
            if (message.isNotEmpty()) add(ParsedField(QrFieldKey.SMS_BODY, message))
        }
        return InspectedBarcodePayload(
            id = id,
            rawContent = rawContent,
            formatLabel = formatLabel,
            category = QrPayloadCategory.SMS,
            summaryTitle = recipient.ifEmpty { truncate(trimmed, SUMMARY_MAX_LENGTH) },
            fields = fields,
            securityNotices = emptyList(),
            scannedAtEpochMs = scannedAtEpochMs,
        )
    }

    private fun parseGeo(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        val coordsPart = trimmed.substringAfter(':', "").substringBefore('?')
        val parts = coordsPart.split(',')
        val lat = parts.getOrNull(0)?.trim().orEmpty()
        val lng = parts.getOrNull(1)?.trim().orEmpty()
        val fields = buildList {
            addAll(baseFields)
            if (lat.isNotEmpty()) add(ParsedField(QrFieldKey.LATITUDE, lat))
            if (lng.isNotEmpty()) add(ParsedField(QrFieldKey.LONGITUDE, lng))
        }
        return InspectedBarcodePayload(
            id = id,
            rawContent = rawContent,
            formatLabel = formatLabel,
            category = QrPayloadCategory.GEO,
            summaryTitle = if (lat.isNotEmpty() && lng.isNotEmpty()) "$lat, $lng" else trimmed,
            fields = fields,
            securityNotices = emptyList(),
            scannedAtEpochMs = scannedAtEpochMs,
        )
    }

    private fun parseJson(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        val parsed = runCatching { jsonParser.parseToJsonElement(trimmed) }.getOrNull()
            ?: return parsePlainText(id, rawContent, formatLabel, trimmed, baseFields, scannedAtEpochMs)

        val keysSummary = when (parsed) {
            is JsonObject -> parsed.keys.joinToString(", ")
            is JsonArray -> "Array(${parsed.size})"
            else -> ""
        }
        val fields = buildList {
            addAll(baseFields)
            if (keysSummary.isNotEmpty()) {
                add(ParsedField(QrFieldKey.JSON_KEYS, keysSummary))
            }
            add(ParsedField(QrFieldKey.TEXT_PREVIEW, truncate(trimmed, TEXT_PREVIEW_MAX_LENGTH)))
        }
        return InspectedBarcodePayload(
            id = id,
            rawContent = rawContent,
            formatLabel = formatLabel,
            category = QrPayloadCategory.JSON,
            summaryTitle = truncate(keysSummary.ifEmpty { trimmed }, SUMMARY_MAX_LENGTH),
            fields = fields,
            securityNotices = emptyList(),
            scannedAtEpochMs = scannedAtEpochMs,
        )
    }

    private fun parsePlainText(
        id: String,
        rawContent: String,
        formatLabel: String,
        trimmed: String,
        baseFields: List<ParsedField>,
        scannedAtEpochMs: Long,
    ): InspectedBarcodePayload {
        val fields = baseFields + ParsedField(
            QrFieldKey.TEXT_PREVIEW,
            truncate(trimmed, TEXT_PREVIEW_MAX_LENGTH),
        )
        return InspectedBarcodePayload(
            id = id,
            rawContent = rawContent,
            formatLabel = formatLabel,
            category = QrPayloadCategory.PLAIN_TEXT,
            summaryTitle = truncate(trimmed, SUMMARY_MAX_LENGTH),
            fields = fields,
            securityNotices = emptyList(),
            scannedAtEpochMs = scannedAtEpochMs,
        )
    }

    private fun looksLikeJson(input: String): Boolean {
        val isObject = input.startsWith('{') && input.endsWith('}')
        val isArray = input.startsWith('[') && input.endsWith(']')
        return isObject || isArray
    }

    private fun isIpAddress(host: String): Boolean =
        ipv4Regex.matches(host) || (host.contains(':') && host.startsWith('[') && host.endsWith(']'))

    private fun isSensitiveKey(key: String): Boolean = key.lowercase() in sensitiveParamKeys

    private fun parseQueryParams(rawQuery: String?): List<Pair<String, String>> {
        if (rawQuery.isNullOrBlank()) {
            return emptyList()
        }
        return rawQuery.split('&').mapNotNull { pair ->
            if (pair.isEmpty()) return@mapNotNull null
            val key = decodeUrlComponent(pair.substringBefore('='))
            val value = decodeUrlComponent(pair.substringAfter('=', ""))
            key to value
        }
    }

    private fun decodeUrlComponent(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

    private fun splitEscaped(input: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaping = false
        for (ch in input) {
            when {
                escaping -> {
                    current.append('\\').append(ch)
                    escaping = false
                }
                ch == '\\' -> escaping = true
                ch == delimiter -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (escaping) {
            current.append('\\')
        }
        if (current.isNotEmpty()) {
            result += current.toString()
        }
        return result
    }

    private fun unescapeWifiValue(value: String): String {
        val out = StringBuilder(value.length)
        var escaping = false
        for (ch in value) {
            if (escaping) {
                out.append(ch)
                escaping = false
            } else if (ch == '\\') {
                escaping = true
            } else {
                out.append(ch)
            }
        }
        if (escaping) {
            out.append('\\')
        }
        return out.toString()
    }

    private fun truncate(text: String, maxLength: Int): String =
        if (text.length <= maxLength) text else text.take(maxLength) + "…"
}
