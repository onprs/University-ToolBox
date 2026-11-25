package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.onprs.universitytoolbox.core.security.TinkAeadCryptoEngine
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.TinkChaoxingCredentialCipher
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EncryptedAccountCookieStorageTest {
    @Test
    fun `encrypts cookies per account in noBackupFilesDir and recovers safely from tampering`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val cryptoEngine = TinkAeadCryptoEngine.createInMemory()
        val storage = EncryptedAccountCookieStorage(
            context = context,
            cryptoEngine = cryptoEngine,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val secretPayload = "[{\"name\":\"vc3\",\"value\":\"sensitive_session_token_999\"}]"
        storage.writeSerializedCookies("cx_2001", secretPayload)

        val vaultDir = File(context.noBackupFilesDir, "chaoxing_cookie_vault")
        val files = vaultDir.listFiles().orEmpty()
        assertEquals(1, files.size)
        val rawDiskText = files.first().readText()
        assertFalse("磁盘密文不得包含明文 Cookie 值", rawDiskText.contains("sensitive_session_token_999"))

        assertEquals(secretPayload, storage.readSerializedCookies("cx_2001"))
        assertNull(storage.readSerializedCookies("cx_2002"))

        // 模拟磁盘文件被篡改：应安全删除损坏文件并返回 null 而非崩溃
        files.first().writeText("corrupted_ciphertext_payload")
        assertNull(storage.readSerializedCookies("cx_2001"))
        assertTrue(vaultDir.listFiles().orEmpty().isEmpty())

        // 验证凭据加解密桥接同样绑定 accountId 关联数据
        val credentialCipher = TinkChaoxingCredentialCipher(cryptoEngine)
        val cipherPwd = credentialCipher.encrypt("sample_pass_123", associatedData = "cx_2001")
        assertFalse(cipherPwd.contains("sample_pass_123"))
        assertEquals("sample_pass_123", credentialCipher.decrypt(cipherPwd, associatedData = "cx_2001"))
    }
}
