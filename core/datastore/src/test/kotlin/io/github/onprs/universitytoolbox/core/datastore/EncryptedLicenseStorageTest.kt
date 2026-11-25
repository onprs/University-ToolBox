package io.github.onprs.universitytoolbox.core.datastore

import io.github.onprs.universitytoolbox.core.security.TinkAeadCryptoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedLicenseStorageTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `activated key, watermark, and revoked ids persist encrypted on disk`() = runTest {
        val engine = TinkAeadCryptoEngine.createInMemory()
        val vaultFile = tempFolder.newFile("license_vault.enc").apply { delete() }
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val storage = EncryptedLicenseStorage.createForTest(
                produceFile = { vaultFile },
                aeadCryptoEngine = engine,
                scope = storeScope,
            )

            assertNull(storage.loadActivatedKey())
            assertNull(storage.loadClockHighWaterMarkEpochSec())
            assertEquals(emptySet<String>(), storage.loadRevokedSerialIds())

            val sampleKey = "DEV-FULL-UNLOCK"
            storage.saveActivatedKey(sampleKey)
            storage.saveClockHighWaterMarkEpochSec(1_760_000_000L)
            storage.saveClockHighWaterMarkEpochSec(1_750_000_000L) // 回拨值不应覆盖更高水位
            storage.saveRevokedSerialIds(setOf("SERIAL-001", "SERIAL-002"))

            assertEquals(sampleKey, storage.loadActivatedKey())
            assertEquals(1_760_000_000L, storage.loadClockHighWaterMarkEpochSec())
            assertEquals(setOf("SERIAL-001", "SERIAL-002"), storage.loadRevokedSerialIds())

            val rawText = vaultFile.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(rawText.contains(sampleKey))
            assertFalse(rawText.contains("SERIAL-001"))

            storage.clearActivatedKey()
            assertNull(storage.loadActivatedKey())
            assertEquals(1_760_000_000L, storage.loadClockHighWaterMarkEpochSec())
        } finally {
            storeScope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun `tampered ciphertext resets license storage to locked empty defaults`() = runTest {
        val engine = TinkAeadCryptoEngine.createInMemory()
        val vaultFile = tempFolder.newFile("tampered_vault.enc").apply { delete() }
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val storage = EncryptedLicenseStorage.createForTest(
            produceFile = { vaultFile },
            aeadCryptoEngine = engine,
            scope = firstScope,
        )

        storage.saveActivatedKey("DEV-STD-UNLOCK")
        assertEquals("DEV-STD-UNLOCK", storage.loadActivatedKey())
        firstScope.coroutineContext[Job]?.cancelAndJoin()

        val diskBytes = vaultFile.readBytes()
        diskBytes[diskBytes.lastIndex] = (diskBytes.last().toInt() xor 0x3C).toByte()
        vaultFile.writeBytes(diskBytes)

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = EncryptedLicenseStorage.createForTest(
                produceFile = { vaultFile },
                aeadCryptoEngine = engine,
                scope = secondScope,
            )

            assertNull(reopened.loadActivatedKey())
            assertNull(reopened.loadClockHighWaterMarkEpochSec())
            assertEquals(emptySet<String>(), reopened.loadRevokedSerialIds())
        } finally {
            secondScope.coroutineContext[Job]?.cancelAndJoin()
        }
    }
}
