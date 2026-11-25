package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.device

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 超星签到设备码（`deviceCode`）与专属客户端会话指纹生成及解析工具。
 */
object ChaoxingDeviceIdentityHelper {
    private const val DEVICE_FLAG_KEY_UTF8 = "QrCbNY@MuK1X8HGw"
    private const val DEVICE_CIPHER_TRANSFORMATION = "AES/ECB/PKCS5Padding"
    private const val AES_ALGORITHM = "AES"
    private const val SHA256_ALGORITHM = "SHA-256"
    private const val MD5_ALGORITHM = "MD5"
    private const val UA_SCHILD_SALT = "ipL\$TkeiEmfy1gTXb2XHrdLN0a@7c^vu"
    private const val ASCII_PRINTABLE_MIN = 0x20
    private const val ASCII_PRINTABLE_MAX = 0x7E
    private const val HEX_BYTE_MASK = 0xFF
    private const val HEX_RADIX = 16
    private const val HEX_PAD_LEN = 2

    private val INVALID_SEED_REGEX = Regex("^0{8,64}$")
    private val UA_DEVICE_MODEL_REGEX = Regex("\\(device:([^)]+)\\)")
    private val UA_UNIQUE_ID_REGEX = Regex("\\(@(?:Kalimdor|Azeroth)\\)_([A-Za-z0-9_-]{8,64})")

    private val MAINSTREAM_MODELS = listOf(
        "24031PN0DC",
        "23127PN0CC",
        "24129PN74C",
        "23113RKC6C",
        "24117RK2CC",
        "ALN-AL00",
        "HBN-AL00",
        "PGT-AN00",
        "BVL-AN00",
        "V2309A",
        "V2324A",
        "V2408A",
        "PHZ110",
        "PKB110",
        "PJA110",
        "PLK110",
        "SM-S9210",
        "SM-S9280",
    )

    /**
     * 基于本机安全标识派生稳定的统一设备码；若系统标识不可用则返回空串交由固定设备码回退。
     */
    @SuppressLint("HardwareIds")
    fun resolveLocalMachineDeviceCode(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty().trim()
        }.getOrDefault("")
        if (androidId.isEmpty() || INVALID_SEED_REGEX.matches(androidId)) {
            return ""
        }
        return deriveDeterministicDeviceCode(androidId)
    }

    /**
     * 使用超星客户端设备标识对称算法将稳定硬件种子编码为设备码。
     */
    @SuppressLint("GetInstance")
    fun deriveDeterministicDeviceCode(seed: String): String {
        val trimmed = seed.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        val cipher = Cipher.getInstance(DEVICE_CIPHER_TRANSFORMATION)
        val keySpec = SecretKeySpec(DEVICE_FLAG_KEY_UTF8.toByteArray(StandardCharsets.UTF_8), AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    /**
     * 生成与超星协议长度兼容的 88 字符独立随机设备码（用于未绑定统一设备码的子账号固定隔离）。
     */
    fun generateRandomDeviceCode(): String {
        val entropy = UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "")
        val digest = MessageDigest.getInstance(SHA256_ALGORITHM)
            .digest(entropy.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest + digest)
    }

    /** 生成客户端会话唯一标识 `uniqueId`。 */
    fun generateUniqueId(): String = UUID.randomUUID().toString().replace("-", "")

    /** 读取本机可读 ASCII 设备型号。 */
    fun localDeviceModel(): String = Build.MODEL.orEmpty()
        .filter { it.code in ASCII_PRINTABLE_MIN..ASCII_PRINTABLE_MAX }
        .trim()
        .ifBlank { "Android" }

    /** 为未指定设备型号的子账号分配固定的主流机型标识。 */
    fun randomMainstreamDeviceModel(): String = MAINSTREAM_MODELS.random()

    /**
     * 构造带 `schild` 校验摘要的超星官方客户端格式 `User-Agent`，确保各绑定账号会话指纹隔离稳定。
     */
    fun buildClientUserAgent(deviceModel: String, uniqueId: String): String {
        val safeModel = deviceModel
            .filter { it.code in ASCII_PRINTABLE_MIN..ASCII_PRINTABLE_MAX }
            .trim()
            .ifBlank { "Android" }
        val safeUniqueId = uniqueId.trim().ifBlank { generateUniqueId() }
        val osRelease = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "14"
        val httpAgent = "Dalvik/2.1.0 (Linux; U; Android $osRelease; $safeModel Build/UP1A.231005.007)"
        val core = "(device:$safeModel) Language/zh_CN com.chaoxing.mobile/" +
            "ChaoXingStudy_3_6.7.5_android_phone_10941_275 (@Kalimdor)_$safeUniqueId"
        val schildInput = "(schild:$UA_SCHILD_SALT) $core"
        val schildHex = md5Hex(schildInput.toByteArray(StandardCharsets.UTF_8))
        return "$httpAgent (schild:$schildHex) $core"
    }

    /**
     * 从历史签到记录返回的 `useragent` 字符串中提取真实设备型号与 `uniqueId`。
     */
    fun parseUserAgentIdentity(rawUserAgent: String?): Pair<String?, String?> {
        if (rawUserAgent.isNullOrBlank()) {
            return null to null
        }
        val model = UA_DEVICE_MODEL_REGEX.find(rawUserAgent)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
        val uniqueId = UA_UNIQUE_ID_REGEX.find(rawUserAgent)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
        return model to uniqueId
    }

    private fun md5Hex(bytes: ByteArray): String = MessageDigest.getInstance(MD5_ALGORITHM)
        .digest(bytes)
        .joinToString("") { byte ->
            (byte.toInt() and HEX_BYTE_MASK).toString(HEX_RADIX).padStart(HEX_PAD_LEN, '0')
        }
}
