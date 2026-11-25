package io.github.onprs.universitytoolbox

import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.tool.api.DefaultToolRegistry
import io.github.onprs.universitytoolbox.feature.chaoxing.api.CHAOXING_TOOL_ID
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.di.ChaoxingModule
import io.github.onprs.universitytoolbox.feature.gpa.api.GPA_TOOL_ID
import io.github.onprs.universitytoolbox.feature.gpa.api.GpaNavKey
import io.github.onprs.universitytoolbox.feature.gpa.impl.di.GpaModule
import io.github.onprs.universitytoolbox.feature.qrcodestudio.api.QR_CODE_STUDIO_TOOL_ID
import io.github.onprs.universitytoolbox.feature.qrcodestudio.api.QrCodeStudioNavKey
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.di.QrCodeStudioModule
import io.github.onprs.universitytoolbox.navigation.DeepLinkParser
import io.github.onprs.universitytoolbox.navigation.DeepLinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppAssemblyValidationTest {

    @Test
    fun `all feature tool descriptors assemble cleanly with unique ids and expected tiers`() {
        val descriptors = setOf(
            ChaoxingModule.provideChaoxingToolDescriptor(),
            GpaModule.provideGpaToolDescriptor(),
            QrCodeStudioModule.provideQrCodeStudioToolDescriptor(),
        )
        val registry = DefaultToolRegistry(descriptors)

        val all = registry.allTools()
        assertEquals(3, all.size)
        assertEquals(
            setOf(CHAOXING_TOOL_ID, GPA_TOOL_ID, QR_CODE_STUDIO_TOOL_ID),
            all.map { it.id }.toSet(),
        )

        val gpa = registry.findById(GPA_TOOL_ID)
        assertNotNull(gpa)
        assertEquals(LicenseTier.FREE, gpa?.requiredTier)
        assertEquals(GpaNavKey, gpa?.entryKey)

        val qrStudio = registry.findById(QR_CODE_STUDIO_TOOL_ID)
        assertNotNull(qrStudio)
        assertEquals(LicenseTier.STANDARD, qrStudio?.requiredTier)
        assertEquals(QrCodeStudioNavKey, qrStudio?.entryKey)

        val chaoxing = registry.findById(CHAOXING_TOOL_ID)
        assertNotNull(chaoxing)
        assertEquals(LicenseTier.STANDARD, chaoxing?.requiredTier)
        assertEquals(ChaoxingNavKey.Dashboard, chaoxing?.entryKey)
    }

    @Test
    fun `feature entry provider installers are all present for host wiring`() {
        val installers = listOf(
            ChaoxingModule.provideChaoxingEntryInstaller(),
            GpaModule.provideGpaEntryInstaller(),
            QrCodeStudioModule.provideQrCodeStudioEntryInstaller(),
        )
        assertEquals(3, installers.size)
    }

    @Test
    fun `deep link parser distinguishes activation links and tool links`() {
        assertEquals(
            DeepLinkTarget.Activation("DEV-STD-UNLOCK"),
            DeepLinkParser.parse("universitytoolbox://unlock?key=DEV-STD-UNLOCK"),
        )
        assertEquals(
            DeepLinkTarget.Activation("CODE-42"),
            DeepLinkParser.parse("universitytoolbox://activate/CODE-42"),
        )
        assertEquals(
            DeepLinkTarget.Activation(null),
            DeepLinkParser.parse("universitytoolbox://license"),
        )
        assertEquals(
            DeepLinkTarget.Tool(GPA_TOOL_ID),
            DeepLinkParser.parse("universitytoolbox://tool/$GPA_TOOL_ID"),
        )
        assertEquals(
            DeepLinkTarget.Tool(CHAOXING_TOOL_ID),
            DeepLinkParser.parse("universitytoolbox://tool?id=$CHAOXING_TOOL_ID"),
        )
        assertNull(DeepLinkParser.parse("universitytoolbox://tool/"))
        assertNull(DeepLinkParser.parse("https://unknown.example.org/other"))
        assertNull(DeepLinkParser.parse("   "))
    }
}
