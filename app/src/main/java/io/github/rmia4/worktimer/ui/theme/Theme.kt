package io.github.rmia4.worktimer.ui.theme

import androidx.core.content.edit
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 크롬 확장 popup.css의 색.
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val page: Color,
    val fg: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val danger: Color,
    // 기록 중 카드(웹앱 .timer-panel)
    val active: Color,
    val activeText: Color,
    val activeMuted: Color,
    val toggleBg: Color,
    val toggleText: Color,
    val toggleLine: Color,
    val activeOutline: Color,
)

private val Light = Palette(
    dark = false,
    bg = Color(0xFFFFFFFF),
    page = Color(0xFFF3F5F9),
    fg = Color(0xFF152746),
    muted = Color(0xFF5D6B82),
    line = Color(0xFFD5DBE5),
    accent = Color(0xFF2F5BD3),
    danger = Color(0xFFC0392B),
    active = Color(0xFF4B5058),
    activeText = Color(0xFFF7F8F9),
    activeMuted = Color(0xFFD5D9DE),
    toggleBg = Color(0xFFFFFFFF),
    toggleText = Color(0xFF24324D),
    toggleLine = Color(0xFFD5DCE8),
    activeOutline = Color(0xFF858C95),
)

private val Dark = Palette(
    dark = true,
    bg = Color(0xFF121A2A),
    page = Color(0xFF0F1419),
    fg = Color(0xFFE8EDF6),
    muted = Color(0xFF9AA7BD),
    line = Color(0xFF2C3A55),
    accent = Color(0xFF88ADFF),
    danger = Color(0xFFFF8A7A),
    active = Color(0xFF262B33),
    activeText = Color(0xFFF7F8F9),
    activeMuted = Color(0xFFD5D9DE),
    toggleBg = Color(0xFF212936),
    toggleText = Color(0xFFF7F8F9),
    toggleLine = Color(0xFF2C3A55),
    activeOutline = Color(0xFF5D6570),
)

class ThemeControl(val palette: Palette, val toggle: () -> Unit)

val LocalTheme = staticCompositionLocalOf { ThemeControl(Light) {} }

// 처음에는 기기 설정을 따르고, 직접 고르면 그 값을 저장한다(theme.js와 같은 규칙).
// 기기의 밝은/어두운 모드가 바뀌면 고른 값을 지우고 기기 설정을 따른다.
@Composable
fun WorkTimerTheme(prefs: SharedPreferences, content: @Composable () -> Unit) {
    val system = isSystemInDarkTheme()
    var chosen by remember { mutableStateOf(prefs.getString(KEY, null)?.let { it == "dark" }) }
    var seenSystem by remember { mutableStateOf(system) }
    if (system != seenSystem) {
        seenSystem = system
        chosen = null
        prefs.edit { remove(KEY) }
    }
    val dark = chosen ?: system
    val palette = if (dark) Dark else Light
    val control = ThemeControl(palette) {
        chosen = !dark
        prefs.edit { putString(KEY, if (!dark) "dark" else "light") }
    }
    val scheme = if (dark) darkColorScheme(primary = palette.accent, background = palette.page, surface = palette.bg)
    else lightColorScheme(primary = palette.accent, background = palette.page, surface = palette.bg)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalTheme provides control, content = content)
    }
}

private const val KEY = "theme"
