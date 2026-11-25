package io.github.onprs.universitytoolbox.core.security

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import java.security.GeneralSecurityException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TinkAeadCryptoEngineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `encrypt and decrypt round-trip preserves plaintext and hides raw bytes`() {
        val engine = TinkAeadCryptoEngine.createInMemory()
        val secret = "sensitive-credential-payload"
        val aad = "account-1"

        val encrypted = engine.encryptString(secret, aad)
        assertFalse(encrypted.contains(secret))
        assertEquals(secret, engine.decryptString(encrypted, aad))
    }

    @Test
    fun `decrypt rejects tampered ciphertext and mismatched associated data`() {
        val engine = TinkAeadCryptoEngine.createInMemory()
        val plaintext = "payload".toByteArray(Charsets.UTF_8)
        val aad = "context-a".toByteArray(Charsets.UTF_8)

        val ciphertext = engine.encrypt(plaintext, aad)
        assertArrayEquals(plaintext, engine.decrypt(ciphertext, aad))

        assertThrows(GeneralSecurityException::class.java) {
            engine.decrypt(ciphertext, "context-b".toByteArray(Charsets.UTF_8))
        }

        val tampered = ciphertext.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0xFF).toByte()
        assertThrows(GeneralSecurityException::class.java) {
            engine.decrypt(tampered, aad)
        }
    }

    @Test
    fun `encrypted datastore hides plaintext on disk and recovers safely when tampered`() = runTest {
        val engine = TinkAeadCryptoEngine.createInMemory()
        val storeFile = tempFolder.newFile("vault.pb").apply { delete() }
        val aad = "vault_v1".toByteArray(Charsets.UTF_8)

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = EncryptedDataStoreFactory.createWithFile(
            produceFile = { storeFile },
            aeadCryptoEngine = engine,
            defaultValue = "",
            associatedData = aad,
            scope = firstScope,
            corruptionHandler = ReplaceFileCorruptionHandler { "" },
            encode = { it.toByteArray(Charsets.UTF_8) },
            decode = { String(it, Charsets.UTF_8) },
        )

        val tokenValue = "session-secret-token-value"
        dataStore.updateData { tokenValue }

        assertEquals(tokenValue, dataStore.data.first())
        val rawDiskText = storeFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(rawDiskText.contains(tokenValue))

        // 关闭第一个 DataStore 作用域以释放底层文件句柄锁，再篡改磁盘密文模拟冷启动恢复
        firstScope.coroutineContext[Job]?.cancelAndJoin()

        val bytes = storeFile.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x5A).toByte()
        storeFile.writeBytes(bytes)

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopenedStore = EncryptedDataStoreFactory.createWithFile(
                produceFile = { storeFile },
                aeadCryptoEngine = engine,
                defaultValue = "",
                associatedData = aad,
                scope = secondScope,
                corruptionHandler = ReplaceFileCorruptionHandler { "" },
                encode = { it.toByteArray(Charsets.UTF_8) },
                decode = { String(it, Charsets.UTF_8) },
            )

            assertEquals("", reopenedStore.data.first())
        } finally {
            secondScope.coroutineContext[Job]?.cancelAndJoin()
        }
    }
}
