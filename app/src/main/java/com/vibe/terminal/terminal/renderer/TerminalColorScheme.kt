package com.vibe.terminal.terminal.renderer

import androidx.compose.ui.graphics.Color

/**
 * 终端配色方案
 */
data class TerminalColorScheme(
    val name: String,
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    val selection: Color,
    val colors: List<Color>  // 16色调色板
) {
    companion object {
        /**
         * 默认暗色主题 (类似 VS Code Dark+)
         */
        val Dark = TerminalColorScheme(
            name = "Dark",
            background = Color(0xFF1E1E1E),
            foreground = Color(0xFFD4D4D4),
            cursor = Color(0xFFAEAFAD),
            selection = Color(0x80264F78),
            colors = listOf(
                Color(0xFF000000), // Black
                Color(0xFFCD3131), // Red
                Color(0xFF0DBC79), // Green
                Color(0xFFE5E510), // Yellow
                Color(0xFF2472C8), // Blue
                Color(0xFFBC3FBC), // Magenta
                Color(0xFF11A8CD), // Cyan
                Color(0xFFE5E5E5), // White
                Color(0xFF666666), // Bright Black
                Color(0xFFF14C4C), // Bright Red
                Color(0xFF23D18B), // Bright Green
                Color(0xFFF5F543), // Bright Yellow
                Color(0xFF3B8EEA), // Bright Blue
                Color(0xFFD670D6), // Bright Magenta
                Color(0xFF29B8DB), // Bright Cyan
                Color(0xFFFFFFFF)  // Bright White
            )
        )

        /**
         * Dracula 主题
         */
        val Dracula = TerminalColorScheme(
            name = "Dracula",
            background = Color(0xFF282A36),
            foreground = Color(0xFFF8F8F2),
            cursor = Color(0xFFF8F8F2),
            selection = Color(0x8044475A),
            colors = listOf(
                Color(0xFF21222C), // Black
                Color(0xFFFF5555), // Red
                Color(0xFF50FA7B), // Green
                Color(0xFFF1FA8C), // Yellow
                Color(0xFFBD93F9), // Blue
                Color(0xFFFF79C6), // Magenta
                Color(0xFF8BE9FD), // Cyan
                Color(0xFFF8F8F2), // White
                Color(0xFF6272A4), // Bright Black
                Color(0xFFFF6E6E), // Bright Red
                Color(0xFF69FF94), // Bright Green
                Color(0xFFFFFFA5), // Bright Yellow
                Color(0xFFD6ACFF), // Bright Blue
                Color(0xFFFF92DF), // Bright Magenta
                Color(0xFFA4FFFF), // Bright Cyan
                Color(0xFFFFFFFF)  // Bright White
            )
        )

        /**
         * Nord 主题
         */
        val Nord = TerminalColorScheme(
            name = "Nord",
            background = Color(0xFF2E3440),
            foreground = Color(0xFFD8DEE9),
            cursor = Color(0xFFD8DEE9),
            selection = Color(0x804C566A),
            colors = listOf(
                Color(0xFF3B4252), // Black
                Color(0xFFBF616A), // Red
                Color(0xFFA3BE8C), // Green
                Color(0xFFEBCB8B), // Yellow
                Color(0xFF81A1C1), // Blue
                Color(0xFFB48EAD), // Magenta
                Color(0xFF88C0D0), // Cyan
                Color(0xFFE5E9F0), // White
                Color(0xFF4C566A), // Bright Black
                Color(0xFFBF616A), // Bright Red
                Color(0xFFA3BE8C), // Bright Green
                Color(0xFFEBCB8B), // Bright Yellow
                Color(0xFF81A1C1), // Bright Blue
                Color(0xFFB48EAD), // Bright Magenta
                Color(0xFF8FBCBB), // Bright Cyan
                Color(0xFFECEFF4)  // Bright White
            )
        )

        /**
         * Solarized Dark 主题
         */
        val SolarizedDark = TerminalColorScheme(
            name = "Solarized Dark",
            background = Color(0xFF002B36),
            foreground = Color(0xFF839496),
            cursor = Color(0xFF839496),
            selection = Color(0x80073642),
            colors = listOf(
                Color(0xFF073642), // Black
                Color(0xFFDC322F), // Red
                Color(0xFF859900), // Green
                Color(0xFFB58900), // Yellow
                Color(0xFF268BD2), // Blue
                Color(0xFFD33682), // Magenta
                Color(0xFF2AA198), // Cyan
                Color(0xFFEEE8D5), // White
                Color(0xFF002B36), // Bright Black
                Color(0xFFCB4B16), // Bright Red
                Color(0xFF586E75), // Bright Green
                Color(0xFF657B83), // Bright Yellow
                Color(0xFF839496), // Bright Blue
                Color(0xFF6C71C4), // Bright Magenta
                Color(0xFF93A1A1), // Bright Cyan
                Color(0xFFFDF6E3)  // Bright White
            )
        )

        val all = listOf(Dark, Dracula, Nord, SolarizedDark)
    }
}
