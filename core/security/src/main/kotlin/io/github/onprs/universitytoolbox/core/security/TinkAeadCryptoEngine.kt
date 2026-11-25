package io.github.onprs.universitytoolbox.core.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.GeneralSecurityException

/**
 * 安全存储规范常量。
 * 宿主应用在配置 `backup_rules` / `data_extraction_rules` 时应排除 [KEYSET_PREF_FILE_NAME]
 * 与 [NO_BACKUP_STORE_SUBDIR]，防止硬件绑定密钥集随云备份迁移导致失效或泄漏。
 */
object SecurityConstants {
    const val KEYSET_NAME = "university_toolbox_master_keyset"
    const val KEYSET_PREF_FILE_NAME = "university_toolbox_tink_keyset_prefs"
    const val MASTER_KEY_URI = "android-keystore://university_toolbox_master_key"
    const val NO_BACKUP_STORE_SUBDIR = "encrypted_datastore"
    const val KEY_TEMPLATE_AES256_GCM = "AES256_GCM"
}

/**
 * 基于 Google Tink AEAD（AES256-GCM）的加密引擎实现。
 * 生产环境通过 Android Keystore 保护主密钥；JVM 单测可使用 [createInMemory] 生成临时密钥集。
 */
class TinkAeadCryptoEngine private constructor(private val aead: Aead) : AeadCryptoEngine {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.encrypt(plaintext, associatedData)

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.decrypt(ciphertext, associatedData)

    companion object {
        private val initLock = Any()

        @Volatile
        private var aeadRegistered = false

        private fun ensureAeadRegistered() {
            if (!aeadRegistered) {
                synchronized(initLock) {
                    if (!aeadRegistered) {
                        AeadConfig.register()
                        aeadRegistered = true
                    }
                }
            }
        }

        /**
         * 创建绑定至 Android Keystore 的硬件级 Tink AEAD 引擎。
         */
        @Throws(GeneralSecurityException::class)
        fun createAndroidKeystore(
            context: Context,
            keysetName: String = SecurityConstants.KEYSET_NAME,
            prefFileName: String = SecurityConstants.KEYSET_PREF_FILE_NAME,
            masterKeyUri: String = SecurityConstants.MASTER_KEY_URI,
        ): TinkAeadCryptoEngine {
            ensureAeadRegistered()
            val keysetHandle = synchronized(initLock) {
                AndroidKeysetManager.Builder()
                    .withSharedPref(context.applicationContext, keysetName, prefFileName)
                    .withKeyTemplate(KeyTemplates.get(SecurityConstants.KEY_TEMPLATE_AES256_GCM))
                    .withMasterKeyUri(masterKeyUri)
                    .build()
                    .keysetHandle
            }
            val primitive = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            return TinkAeadCryptoEngine(primitive)
        }

        /**
         * 创建仅驻留内存的 AES256-GCM Tink 引擎，供 JVM 单元测试验证真实加解密与防篡改行为。
         */
        fun createInMemory(): TinkAeadCryptoEngine {
            ensureAeadRegistered()
            val keysetHandle = KeysetHandle.generateNew(KeyTemplates.get(SecurityConstants.KEY_TEMPLATE_AES256_GCM))
            val primitive = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            return TinkAeadCryptoEngine(primitive)
        }
    }
}
