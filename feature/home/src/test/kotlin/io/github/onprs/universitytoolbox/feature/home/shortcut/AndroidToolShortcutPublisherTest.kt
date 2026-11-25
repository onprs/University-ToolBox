package io.github.onprs.universitytoolbox.feature.home.shortcut

import android.app.Application
import android.content.pm.ShortcutManager
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import io.github.onprs.universitytoolbox.core.license.api.AccessDenialReason
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.tool.api.ToolCategory
import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor
import io.github.onprs.universitytoolbox.feature.home.R
import io.github.onprs.universitytoolbox.feature.home.domain.HomeToolItem
import io.github.onprs.universitytoolbox.feature.home.domain.ToolAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidToolShortcutPublisherTest {
    private data object SampleKey : NavKey

    @Test
    fun `publishes only available tools up to dynamic shortcut limit`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val publisher = AndroidToolShortcutPublisher(context)

        val availableTool = HomeToolItem(
            descriptor = ToolDescriptor(
                id = "gpa_calculator",
                titleRes = R.string.feature_home_title,
                descriptionRes = R.string.feature_home_action_open,
                category = ToolCategory.ACADEMIC,
                requiredTier = LicenseTier.FREE,
                entryKey = SampleKey,
                sortOrder = 1,
            ),
            accessStatus = ToolAccessStatus.Available,
            isPinned = true,
            lastUsedAtEpochMs = null,
        )
        val lockedTool = HomeToolItem(
            descriptor = ToolDescriptor(
                id = "qrcode_studio",
                titleRes = R.string.feature_home_open_license,
                descriptionRes = R.string.feature_home_action_unlock,
                category = ToolCategory.MEDIA_UTILITY,
                requiredTier = LicenseTier.STANDARD,
                entryKey = SampleKey,
                sortOrder = 2,
            ),
            accessStatus = ToolAccessStatus.Locked(AccessDenialReason.Expired),
            isPinned = false,
            lastUsedAtEpochMs = null,
        )

        publisher.publishShortcuts(listOf(availableTool, lockedTool))

        val manager = context.getSystemService(ShortcutManager::class.java)
        val idsFromManager = manager?.dynamicShortcuts?.map { it.id }
            ?: ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }
        assertEquals(listOf("tool_gpa_calculator"), idsFromManager)
        assertFalse("tool_qrcode_studio" in idsFromManager)

        val intentUri = ShortcutManagerCompat.getDynamicShortcuts(context).single().intent?.dataString
        assertTrue(intentUri == "universitytoolbox://tool/gpa_calculator")
    }
}
