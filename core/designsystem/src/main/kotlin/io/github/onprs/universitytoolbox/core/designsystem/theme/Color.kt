package io.github.onprs.universitytoolbox.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 品牌主色阶：学术靛蓝与青碧辅色
private val PrimaryLight = Color(0xFF2852A8)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val PrimaryContainerLight = Color(0xFFD9E2FF)
private val OnPrimaryContainerLight = Color(0xFF001945)

private val SecondaryLight = Color(0xFF4F5E78)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFD6E3FF)
private val OnSecondaryContainerLight = Color(0xFF0B1C32)

private val TertiaryLight = Color(0xFF0E746C)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFA2F2E8)
private val OnTertiaryContainerLight = Color(0xFF00201D)

private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)

private val BackgroundLight = Color(0xFFF9F9FE)
private val OnBackgroundLight = Color(0xFF191C22)
private val SurfaceLight = Color(0xFFF9F9FE)
private val OnSurfaceLight = Color(0xFF191C22)
private val SurfaceVariantLight = Color(0xFFE1E2EC)
private val OnSurfaceVariantLight = Color(0xFF44474F)
private val OutlineLight = Color(0xFF74777F)
private val OutlineVariantLight = Color(0xFFC4C6D0)

private val PrimaryDark = Color(0xFFB0C6FF)
private val OnPrimaryDark = Color(0xFF002C71)
private val PrimaryContainerDark = Color(0xFF073B8F)
private val OnPrimaryContainerDark = Color(0xFFD9E2FF)

private val SecondaryDark = Color(0xFFB7C7E4)
private val OnSecondaryDark = Color(0xFF213048)
private val SecondaryContainerDark = Color(0xFF384760)
private val OnSecondaryContainerDark = Color(0xFFD6E3FF)

private val TertiaryDark = Color(0xFF86D6CC)
private val OnTertiaryDark = Color(0xFF003733)
private val TertiaryContainerDark = Color(0xFF00504A)
private val OnTertiaryContainerDark = Color(0xFFA2F2E8)

private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

private val BackgroundDark = Color(0xFF111318)
private val OnBackgroundDark = Color(0xFFE2E2E9)
private val SurfaceDark = Color(0xFF111318)
private val OnSurfaceDark = Color(0xFFE2E2E9)
private val SurfaceVariantDark = Color(0xFF44474F)
private val OnSurfaceVariantDark = Color(0xFFC4C6D0)
private val OutlineDark = Color(0xFF8E9099)
private val OutlineVariantDark = Color(0xFF44474F)

/** 浅色模式默认 Material 3 配色方案。 */
val ToolboxLightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

/** 深色模式默认 Material 3 配色方案。 */
val ToolboxDarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

/**
 * 业务语义扩展色板（成功、警示、提示状态），补充标准 Material 3 色彩角色。
 */
@Immutable
data class ToolboxExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

val LightExtendedColors = ToolboxExtendedColors(
    success = Color(0xFF1B7A43),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFC8F7D6),
    onSuccessContainer = Color(0xFF00210E),
    warning = Color(0xFF9E5A00),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDDB7),
    onWarningContainer = Color(0xFF331A00),
    info = Color(0xFF1D629C),
    onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFD0E4FF),
    onInfoContainer = Color(0xFF001D36),
)

val DarkExtendedColors = ToolboxExtendedColors(
    success = Color(0xFF83DA9F),
    onSuccess = Color(0xFF00391B),
    successContainer = Color(0xFF00522A),
    onSuccessContainer = Color(0xFFC8F7D6),
    warning = Color(0xFFFFB963),
    onWarning = Color(0xFF542E00),
    warningContainer = Color(0xFF774300),
    onWarningContainer = Color(0xFFFFDDB7),
    info = Color(0xFF9ACBFF),
    onInfo = Color(0xFF00325A),
    infoContainer = Color(0xFF004980),
    onInfoContainer = Color(0xFFD0E4FF),
)

val LocalToolboxExtendedColors = staticCompositionLocalOf { LightExtendedColors }
