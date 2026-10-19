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

enum class ThemeMode { Light, Dark, System }

class ThemeControl(val palette: Palette, val mode: ThemeMode, val select: (ThemeMode) -> Unit)

val LocalTheme = staticCompositionLocalOf { ThemeControl(Light, ThemeMode.System) {} }

// 라이트·다크를 고르면 그 값을 저장하고, 기기 설정을 고르면 저장한 값을 지워 기기의 밝은/어두운 모드를 따른다.
// 기기 모드가 바뀌면 isSystemInDarkTheme()이 바뀌어 기기 설정일 때 바로 반영된다.
@Composable
fun WorkTimerTheme(prefs: SharedPreferences, content: @Composable () -> Unit) {
    val system = isSystemInDarkTheme()
    var mode by remember {
        mutableStateOf(when (prefs.getString(KEY, null)) { "dark" -> ThemeMode.Dark; "light" -> ThemeMode.Light; else -> ThemeMode.System })
    }
    val dark = when (mode) { ThemeMode.Dark -> true; ThemeMode.Light -> false; ThemeMode.System -> system }
    val palette = if (dark) Dark else Light
    val control = ThemeControl(palette, mode) { m ->
        mode = m
        prefs.edit {
            when (m) {
                ThemeMode.Dark -> putString(KEY, "dark")
                ThemeMode.Light -> putString(KEY, "light")
                ThemeMode.System -> remove(KEY)
            }
        }
    }
    val scheme = if (dark) darkColorScheme(primary = palette.accent, background = palette.page, surface = palette.bg)
    else lightColorScheme(primary = palette.accent, background = palette.page, surface = palette.bg)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalTheme provides control, content = content)
    }
}

private const val KEY = "theme"
