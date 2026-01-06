package com.vibe.terminal.terminal.emulator

/**
 * 终端字符属性
 */
data class CharacterAttribute(
    val foregroundColor: TerminalColor = TerminalColor.Default,
    val backgroundColor: TerminalColor = TerminalColor.Default,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val dim: Boolean = false,
    val inverse: Boolean = false,
    val hidden: Boolean = false
) {
    companion object {
        val DEFAULT = CharacterAttribute()
    }
}

/**
 * 终端颜色
 */
sealed class TerminalColor {
    data object Default : TerminalColor()
    data class Indexed(val index: Int) : TerminalColor()  // 0-255
    data class TrueColor(val r: Int, val g: Int, val b: Int) : TerminalColor()

    companion object {
        // 基础16色
        val BLACK = Indexed(0)
        val RED = Indexed(1)
        val GREEN = Indexed(2)
        val YELLOW = Indexed(3)
        val BLUE = Indexed(4)
        val MAGENTA = Indexed(5)
        val CYAN = Indexed(6)
        val WHITE = Indexed(7)

        // 明亮色
        val BRIGHT_BLACK = Indexed(8)
        val BRIGHT_RED = Indexed(9)
        val BRIGHT_GREEN = Indexed(10)
        val BRIGHT_YELLOW = Indexed(11)
        val BRIGHT_BLUE = Indexed(12)
        val BRIGHT_MAGENTA = Indexed(13)
        val BRIGHT_CYAN = Indexed(14)
        val BRIGHT_WHITE = Indexed(15)
    }
}
