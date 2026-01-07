package com.vibe.terminal.terminal.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.vibe.terminal.R
import com.vibe.terminal.terminal.emulator.TerminalColor
import com.vibe.terminal.terminal.emulator.TerminalEmulator
import kotlinx.coroutines.delay

/**
 * Terminal font family with Nerd Font support
 * Uses Iosevka Nerd Font for icons and symbols
 */
val TerminalFontFamily = FontFamily(
    Font(R.font.iosevka_nerd, FontWeight.Normal),
    Font(R.font.iosevka_nerd_bold, FontWeight.Bold)
)

/**
 * Check if a character is a wide character (CJK, fullwidth, etc.)
 * Wide characters typically occupy 2 cells in terminal
 */
private fun isWideChar(char: Char): Boolean {
    val code = char.code
    return when {
        // CJK Unified Ideographs
        code in 0x4E00..0x9FFF -> true
        // CJK Unified Ideographs Extension A
        code in 0x3400..0x4DBF -> true
        // CJK Compatibility Ideographs
        code in 0xF900..0xFAFF -> true
        // Hangul Syllables
        code in 0xAC00..0xD7AF -> true
        // Fullwidth Forms
        code in 0xFF00..0xFFEF -> true
        // CJK Symbols and Punctuation
        code in 0x3000..0x303F -> true
        // Hiragana
        code in 0x3040..0x309F -> true
        // Katakana
        code in 0x30A0..0x30FF -> true
        // Bopomofo
        code in 0x3100..0x312F -> true
        // CJK Radicals
        code in 0x2E80..0x2EFF -> true
        // Enclosed CJK Letters
        code in 0x3200..0x32FF -> true
        else -> false
    }
}

/**
 * Get the appropriate font family for a character
 * Uses system default font only for CJK characters (needs Noto Sans CJK)
 * Iosevka Nerd Font has extensive Unicode coverage for other symbols
 */
private fun getFontForChar(char: Char): FontFamily {
    return if (isWideChar(char)) {
        FontFamily.Default  // CJK uses system font
    } else {
        TerminalFontFamily  // Iosevka has good symbol coverage
    }
}

/**
 * 终端渲染组件
 */
@Composable
fun TerminalRenderer(
    emulator: TerminalEmulator,
    colorScheme: TerminalColorScheme = TerminalColorScheme.Dark,
    fontSize: Float = 14f,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // 计算字符尺寸
    val charSize = remember(fontSize) {
        with(density) {
            val style = TextStyle(
                fontFamily = TerminalFontFamily,
                fontSize = fontSize.sp
            )
            val result = textMeasurer.measure("M", style)
            CharSize(
                width = result.size.width.toFloat(),
                height = result.size.height.toFloat()
            )
        }
    }

    // 监听更新
    var updateTrigger by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        emulator.updateSignal.collect { updateTrigger = it }
    }

    // 光标闪烁
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530)
            cursorVisible = !cursorVisible
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
    ) {
        // 触发重绘
        updateTrigger.let { _ ->
            drawTerminal(
                emulator = emulator,
                colorScheme = colorScheme,
                charSize = charSize,
                textMeasurer = textMeasurer,
                fontSize = fontSize,
                cursorVisible = cursorVisible
            )
        }
    }
}

private fun DrawScope.drawTerminal(
    emulator: TerminalEmulator,
    colorScheme: TerminalColorScheme,
    charSize: CharSize,
    textMeasurer: TextMeasurer,
    fontSize: Float,
    cursorVisible: Boolean
) {
    // 确保画布有有效尺寸
    val canvasWidth = size.width
    val canvasHeight = size.height
    if (canvasWidth <= 0 || canvasHeight <= 0 || charSize.width <= 0 || charSize.height <= 0) {
        return
    }

    val rows = emulator.rows
    val cols = emulator.columns

    for (row in 0 until rows) {
        val y = row * charSize.height
        // 跳过超出画布的行
        if (y >= canvasHeight) break
        if (y + charSize.height < 0) continue

        for (col in 0 until cols) {
            val x = col * charSize.width
            // 跳过超出画布的列
            if (x >= canvasWidth) break
            if (x + charSize.width < 0) continue

            val cell = emulator.getCell(row, col)

            // 绘制背景
            val bgColor = when (val bg = cell.attribute.backgroundColor) {
                is TerminalColor.Default -> colorScheme.background
                is TerminalColor.Indexed -> getIndexedColor(bg.index, colorScheme)
                is TerminalColor.TrueColor -> Color(bg.r, bg.g, bg.b)
            }

            if (bgColor != colorScheme.background) {
                drawRect(
                    color = bgColor,
                    topLeft = Offset(x, y),
                    size = Size(charSize.width, charSize.height)
                )
            }

            // 绘制字符 - 确保有足够空间
            if (cell.character != ' ' && cell.character != '\u0000') {
                // 只在有足够空间时绘制文本
                val availableWidth = canvasWidth - x
                if (availableWidth > 0) {
                    val fgColor = when (val fg = cell.attribute.foregroundColor) {
                        is TerminalColor.Default -> colorScheme.foreground
                        is TerminalColor.Indexed -> getIndexedColor(fg.index, colorScheme)
                        is TerminalColor.TrueColor -> Color(fg.r, fg.g, fg.b)
                    }

                    // 根据字符类型选择字体
                    val fontFamily = getFontForChar(cell.character)
                    val isWide = isWideChar(cell.character)

                    val style = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = fontSize.sp,
                        fontWeight = if (cell.attribute.bold) FontWeight.Bold else FontWeight.Normal,
                        color = fgColor
                    )

                    // 宽字符占用2个cell的宽度
                    if (isWide) {
                        // 绘制宽字符背景（2个cell宽）
                        if (bgColor != colorScheme.background) {
                            drawRect(
                                color = bgColor,
                                topLeft = Offset(x, y),
                                size = Size(charSize.width * 2, charSize.height)
                            )
                        }
                    }

                    drawText(
                        textMeasurer = textMeasurer,
                        text = cell.character.toString(),
                        style = style,
                        topLeft = Offset(x, y)
                    )
                }
            }
        }
    }

    // 绘制光标
    if (cursorVisible) {
        val cursorX = emulator.cursorCol * charSize.width
        val cursorY = emulator.cursorRow * charSize.height

        // 只在画布内绘制光标
        if (cursorX >= 0 && cursorX < canvasWidth && cursorY >= 0 && cursorY < canvasHeight) {
            drawRect(
                color = colorScheme.cursor,
                topLeft = Offset(cursorX, cursorY),
                size = Size(charSize.width, charSize.height),
                alpha = 0.7f
            )
        }
    }
}

private fun getIndexedColor(index: Int, colorScheme: TerminalColorScheme): Color {
    return when {
        index < 16 -> colorScheme.colors.getOrElse(index) { colorScheme.foreground }
        index < 232 -> {
            // 216 色立方体 (6x6x6)
            val adjusted = index - 16
            val r = (adjusted / 36) * 51
            val g = ((adjusted / 6) % 6) * 51
            val b = (adjusted % 6) * 51
            Color(r, g, b)
        }
        else -> {
            // 24级灰度
            val gray = (index - 232) * 10 + 8
            Color(gray, gray, gray)
        }
    }
}

private data class CharSize(
    val width: Float,
    val height: Float
)
