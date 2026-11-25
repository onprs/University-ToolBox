package io.github.onprs.universitytoolbox.core.license.stub

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidDeviceIdentityProviderTest {
    @Test
    fun `derives deterministic dev stub challenge from android id`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "unit-test-android-id")

        val provider = AndroidDeviceIdentityProvider(context)
        val first = provider.getStubDeviceChallenge()
        val second = provider.getStubDeviceChallenge()

        assertEquals(first, second)
        assertTrue(first.startsWith("DEV-STUB-"))
        assertEquals("DEV-STUB-".length + 8, first.length)
    }
}
