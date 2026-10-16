package com.locctl.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = Cyan80,
    onPrimary = Bg0,
    primaryContainer = Cyan40,
    onPrimaryContainer = Text1,
    secondary = Purple,
    onSecondary = Bg0,
    tertiary = Green,
    onTertiary = Bg0,
    background = Bg0,
    onBackground = Text1,
    surface = Bg1,
    onSurface = Text1,
    surfaceVariant = Bg2,
    onSurfaceVariant = Text2,
    outline = Text3,
    outlineVariant = Bg2,
    error = Red,
    onError = Bg0,
)

// 浅色主题刻意保持低对比的灰蓝，因为工具类应用主要在暗光环境用
private val LightScheme = lightColorScheme(
    primary = Cyan60,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBFA),
    onPrimaryContainer = Cyan40,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1B1F24),
    surface = Color.White,
    onSurface = Color(0xFF1B1F24),
    surfaceVariant = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF59636E),
    outline = Color(0xFF8C959F),
    error = Color(0xFFCF222E),
    onError = Color.White,
)

private val LocctlTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

/** 等宽字体样式：坐标、日志、终端输出都用它 */
val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)

@Composable
fun LocctlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = LocctlTypography,
        content = content,
    )
}
