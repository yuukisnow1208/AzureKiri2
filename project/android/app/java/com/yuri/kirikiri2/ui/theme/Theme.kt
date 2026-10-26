package com.yuri.kirikiri2.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 主题。
 *
 * 配色优先级：Android 12+ 的动态取色（壁纸） > 内置的 Violet 种子色。
 * 这内置的种子色与 UI 原型、以及 C++ 侧 TVPTheme 的默认种子保持一致
 * （#6F5BFF，即 RGB 111/91/255）。
 */

private val SeedPrimary = Color(0xFF6F5BFF)
private val SeedSecondary = Color(0xFF65558C)
private val SeedTertiary = Color(0xFF8E4C73)

private val LightColors = lightColorScheme(
    primary = SeedPrimary,
    secondary = SeedSecondary,
    tertiary = SeedTertiary
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFC0FF),
    secondary = Color(0xFFCEC2F0),
    tertiary = Color(0xFFF0B8D8)
)

/** Expressive 风格：标题更重、正文行高更宽松 */
private val Kr2Typography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun Kr2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Kr2Typography,
        content = content
    )
}
