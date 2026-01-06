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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.vibe.terminal.terminal.emulator.TerminalColor
import com.vibe.terminal.terminal.emulator.TerminalEmulator
import kotlinx.coroutines.delay

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
                fontFamily = FontFamily.Monospace,
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
    val rows = emulator.rows
    val cols = emulator.columns

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val cell = emulator.getCell(row, col)
            val x = col * charSize.width
            val y = row * charSize.height

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

            // 绘制字符
            if (cell.character != ' ' && cell.character != '\u0000') {
                val fgColor = when (val fg = cell.attribute.foregroundColor) {
                    is TerminalColor.Default -> colorScheme.foreground
                    is TerminalColor.Indexed -> getIndexedColor(fg.index, colorScheme)
                    is TerminalColor.TrueColor -> Color(fg.r, fg.g, fg.b)
                }

                val style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    fontWeight = if (cell.attribute.bold) FontWeight.Bold else FontWeight.Normal,
                    color = fgColor
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = cell.character.toString(),
                    style = style,
                    topLeft = Offset(x, y)
                )
            }
        }
    }

    // 绘制光标
    if (cursorVisible) {
        val cursorX = emulator.cursorCol * charSize.width
        val cursorY = emulator.cursorRow * charSize.height

        drawRect(
            color = colorScheme.cursor,
            topLeft = Offset(cursorX, cursorY),
            size = Size(charSize.width, charSize.height),
            alpha = 0.7f
        )
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
