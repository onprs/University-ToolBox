package io.github.onprs.universitytoolbox.core.designsystem.theme

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemTokensTest {
    @Test
    fun `light and dark color schemes define distinct primary and surface colors`() {
        assertNotEquals(ToolboxLightColorScheme.primary, ToolboxDarkColorScheme.primary)
        assertNotEquals(ToolboxLightColorScheme.surface, ToolboxDarkColorScheme.surface)
        assertNotEquals(ToolboxLightColorScheme.background, ToolboxLightColorScheme.onBackground)
        assertNotEquals(ToolboxDarkColorScheme.background, ToolboxDarkColorScheme.onBackground)
    }

    @Test
    fun `extended semantic colors remain distinguishable from background`() {
        assertNotEquals(LightExtendedColors.success, LightExtendedColors.successContainer)
        assertNotEquals(LightExtendedColors.warning, LightExtendedColors.warningContainer)
        assertNotEquals(DarkExtendedColors.success, DarkExtendedColors.successContainer)
        assertNotEquals(DarkExtendedColors.info, DarkExtendedColors.infoContainer)
    }

    @Test
    fun `spacing tokens increase monotonically`() {
        assertTrue(ToolboxSpacing.none < ToolboxSpacing.extraSmall)
        assertTrue(ToolboxSpacing.extraSmall < ToolboxSpacing.small)
        assertTrue(ToolboxSpacing.small < ToolboxSpacing.mediumSmall)
        assertTrue(ToolboxSpacing.mediumSmall < ToolboxSpacing.medium)
        assertTrue(ToolboxSpacing.medium < ToolboxSpacing.large)
        assertTrue(ToolboxSpacing.large < ToolboxSpacing.extraLarge)
    }

    @Test
    fun `typography and shapes preserve readable scale`() {
        assertTrue(ToolboxTypography.displaySmall.fontSize > ToolboxTypography.titleLarge.fontSize)
        assertTrue(ToolboxTypography.titleLarge.fontSize > ToolboxTypography.bodyMedium.fontSize)
        assertTrue(ToolboxTypography.bodyMedium.fontSize > ToolboxTypography.labelSmall.fontSize)
        assertNotEquals(ToolboxShapes.extraSmall, ToolboxShapes.extraLarge)
    }
}
