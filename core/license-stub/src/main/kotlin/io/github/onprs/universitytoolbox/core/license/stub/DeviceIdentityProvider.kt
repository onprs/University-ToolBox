package io.github.onprs.universitytoolbox.core.license.stub

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 调试桩设备特征提供契约，仅用于生成本地开发识别占位码，不包含任何正式签发算法。
 */
fun interface DeviceIdentityProvider {
    fun getStubDeviceChallenge(): String
}

/**
 * 基于应用作用域 ANDROID_ID 派生占位设备质询码的 Android 默认实现。
 */
@Singleton
class AndroidDeviceIdentityProvider @Inject constructor(@param:ApplicationContext private val context: Context) :
    DeviceIdentityProvider {
    @SuppressLint("HardwareIds")
    override fun getStubDeviceChallenge(): String {
        val rawAndroidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty().ifBlank { FALLBACK_SEED }

        val input = "${context.packageName}:$rawAndroidId"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hexSegment = digest.take(CHALLENGE_BYTES).joinToString(separator = "") { byte ->
            "%02X".format(byte.toInt() and BYTE_MASK)
        }
        return "$CHALLENGE_PREFIX$hexSegment"
    }

    companion object {
        private const val FALLBACK_SEED = "LOCAL-DEV-DEVICE"
        private const val CHALLENGE_PREFIX = "DEV-STUB-"
        private const val CHALLENGE_BYTES = 4
        private const val BYTE_MASK = 0xFF
    }
}
