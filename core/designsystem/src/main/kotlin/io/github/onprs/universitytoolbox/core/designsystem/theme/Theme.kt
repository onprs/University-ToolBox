package io.github.onprs.universitytoolbox.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * 应用全局主题容器，支持系统/显式深浅色切换与 Android 12+ 动态取色。
 */
@Composable
fun UniversityToolboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ToolboxDarkColorScheme
        else -> ToolboxLightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalToolboxExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ToolboxShapes,
            typography = ToolboxTypography,
            content = content,
        )
    }
}

/**
 * 设计系统主题扩展属性便捷访问入口。
 */
object ToolboxTheme {
    val extendedColors: ToolboxExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalToolboxExtendedColors.current
}
