package com.vibe.terminal.terminal.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vibe.terminal.terminal.emulator.TerminalColor
import com.vibe.terminal.terminal.emulator.TerminalEmulator

/**
 * VS Code style minimap for terminal
 * Shows a thumbnail view of the terminal content with current viewport indicator
 *
 * 在 alternate screen mode (zellij/vim/tmux) 下，只渲染当前屏幕内容
 * 在普通模式下，渲染 scrollback + 当前屏幕
 */
@Composable
fun TerminalMinimap(
    emulator: TerminalEmulator,
    colorScheme: TerminalColorScheme = TerminalColorScheme.Dark,
    modifier: Modifier = Modifier,
    width: Int = 60,
    onScrollTo: (Int) -> Unit = {}
) {
    // 监听更新
    var updateTrigger by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        emulator.updateSignal.collect { updateTrigger = it }
    }

    // 在 alternate screen mode 下禁用点击滚动（因为没有 scrollback）
    val isAlternateScreen = emulator.isAlternateScreenMode

    Canvas(
        modifier = modifier
            .width(width.dp)
            .fillMaxHeight()
            .background(colorScheme.background.copy(alpha = 0.9f))
            .pointerInput(isAlternateScreen) {
                if (!isAlternateScreen) {
                    detectTapGestures { offset ->
                        handleMinimapClick(emulator, offset.y, size.height.toFloat(), onScrollTo)
                    }
                }
            }
            .pointerInput(isAlternateScreen) {
                if (!isAlternateScreen) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        handleMinimapClick(emulator, change.position.y, size.height.toFloat(), onScrollTo)
                    }
                }
            }
    ) {
        updateTrigger.let { _ ->
            if (isAlternateScreen) {
                drawMinimapAlternateScreen(emulator, colorScheme)
            } else {
                drawMinimap(emulator, colorScheme)
            }
        }
    }
}

private fun handleMinimapClick(
    emulator: TerminalEmulator,
    clickY: Float,
    canvasHeight: Float,
    onScrollTo: (Int) -> Unit
) {
    val scrollbackSize = emulator.maxScrollOffset
    val screenRows = emulator.rows
    val totalLines = scrollbackSize + screenRows

    if (totalLines > 0 && canvasHeight > 0) {
        // Calculate which absolute line was clicked
        val clickedAbsoluteLine = (clickY / canvasHeight * totalLines).toInt()
            .coerceIn(0, totalLines - 1)

        // Convert to scroll offset
        // scrollOffset = 0 means showing current screen (bottom)
        // scrollOffset = scrollbackSize means showing top of history
        val targetScrollOffset = (scrollbackSize - clickedAbsoluteLine + screenRows / 2)
            .coerceIn(0, scrollbackSize)

        emulator.setScrollOffset(targetScrollOffset)
    }
}

/**
 * 在 alternate screen mode (zellij/vim/tmux) 下渲染 minimap
 * 只渲染当前屏幕内容，不包括 scrollback
 * 使用 VS Code 风格的细腻渲染 - 用细小的点/线代替方块
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMinimapAlternateScreen(
    emulator: TerminalEmulator,
    colorScheme: TerminalColorScheme
) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    if (canvasWidth <= 0 || canvasHeight <= 0) return

    val screenRows = emulator.rows
    val cols = emulator.columns

    if (screenRows <= 0 || cols <= 0) return

    // VS Code style: each character is represented by 1-2 pixel width dots
    // Line height is about 2-4 pixels for readability
    val lineHeight = (canvasHeight / screenRows).coerceIn(2f, 6f)
    val charWidth = (canvasWidth / cols).coerceIn(1f, 2f)

    // Dot dimensions - much smaller than full cell for refined look
    val dotWidth = (charWidth * 0.6f).coerceIn(0.8f, 1.5f)
    val dotHeight = (lineHeight * 0.5f).coerceIn(1f, 2.5f)

    // Draw current screen content with VS Code style dots
    for (row in 0 until screenRows) {
        val y = row * lineHeight + (lineHeight - dotHeight) / 2  // Center vertically
        if (y >= canvasHeight) break

        for (colIdx in 0 until cols) {
            val x = colIdx * charWidth
            val cell = emulator.getCell(row, colIdx)

            // Get background color - draw subtle background for non-default colors
            val bgColor = getTerminalColor(cell.attribute.backgroundColor, colorScheme)
            if (bgColor != null && bgColor != colorScheme.background) {
                drawRect(
                    color = bgColor.copy(alpha = 0.4f),
                    topLeft = Offset(x, row * lineHeight),
                    size = Size(charWidth, lineHeight)
                )
            }

            // Draw foreground as small dot/line for non-empty characters
            if (cell.character.isNotBlank() && cell.character != " " && cell.character != "\u0000") {
                val fgColor = getTerminalColor(cell.attribute.foregroundColor, colorScheme)
                    ?: colorScheme.foreground

                // Use slightly transparent color for smoother appearance
                drawRect(
                    color = fgColor.copy(alpha = 0.85f),
                    topLeft = Offset(x, y),
                    size = Size(dotWidth, dotHeight)
                )
            }
        }
    }

    // Draw a subtle border to indicate this is the current panel view
    drawRect(
        color = Color(0xFF569CD6).copy(alpha = 0.5f),
        topLeft = Offset(0f, 0f),
        size = Size(2f, canvasHeight)
    )
}

/**
 * 普通模式下渲染 minimap (包括 scrollback)
 * 使用 VS Code 风格的细腻渲染 - 用细小的点/线代替方块
 * 针对大量历史记录进行性能优化
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMinimap(
    emulator: TerminalEmulator,
    colorScheme: TerminalColorScheme
) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    if (canvasWidth <= 0 || canvasHeight <= 0) return

    val scrollbackSize = emulator.maxScrollOffset
    val screenRows = emulator.rows
    val totalLines = scrollbackSize + screenRows
    val cols = emulator.columns

    if (totalLines <= 0 || cols <= 0) return

    // VS Code style dimensions
    val lineHeight = (canvasHeight / totalLines).coerceIn(1f, 4f)
    val charWidth = (canvasWidth / cols).coerceIn(0.5f, 2f)

    // Dot dimensions for refined look
    val dotWidth = (charWidth * 0.6f).coerceIn(0.5f, 1.2f)
    val dotHeight = (lineHeight * 0.5f).coerceIn(0.5f, 2f)

    // Determine sampling step based on total lines for performance
    val lineStep = when {
        totalLines > 10000 -> 8
        totalLines > 5000 -> 4
        totalLines > 2000 -> 2
        else -> 1
    }

    // Column sampling for very wide terminals
    val colStep = when {
        cols > 300 -> 3
        cols > 200 -> 2
        else -> 1
    }

    // Draw all lines (scrollback + current screen) with VS Code style dots
    for (lineIdx in 0 until totalLines step lineStep) {
        val baseY = lineIdx * lineHeight
        val y = baseY + (lineHeight * lineStep - dotHeight) / 2  // Center vertically
        if (baseY >= canvasHeight) break

        for (colIdx in 0 until cols step colStep) {
            val x = colIdx * charWidth
            val cell = emulator.getCellAbsolute(lineIdx, colIdx)

            val effectiveDotWidth = (dotWidth * colStep).coerceIn(0.5f, 2f)
            val effectiveDotHeight = (dotHeight * lineStep).coerceIn(0.5f, 3f)

            // Get background color - draw subtle background for non-default colors
            val bgColor = getTerminalColor(cell.attribute.backgroundColor, colorScheme)
            if (bgColor != null && bgColor != colorScheme.background) {
                drawRect(
                    color = bgColor.copy(alpha = 0.35f),
                    topLeft = Offset(x, baseY),
                    size = Size(charWidth * colStep, lineHeight * lineStep)
                )
            }

            // Draw foreground as small dot for non-empty characters
            if (cell.character.isNotBlank() && cell.character != " " && cell.character != "\u0000") {
                val fgColor = getTerminalColor(cell.attribute.foregroundColor, colorScheme)
                    ?: colorScheme.foreground

                drawRect(
                    color = fgColor.copy(alpha = 0.8f),
                    topLeft = Offset(x, y),
                    size = Size(effectiveDotWidth, effectiveDotHeight)
                )
            }
        }
    }

    // Draw viewport indicator (current visible area)
    val viewportStartLine = scrollbackSize - emulator.scrollOffset
    val viewportY = viewportStartLine * lineHeight
    val viewportHeight = screenRows * lineHeight

    // Viewport background highlight - subtle
    drawRect(
        color = Color.White.copy(alpha = 0.1f),
        topLeft = Offset(0f, viewportY),
        size = Size(canvasWidth, viewportHeight)
    )

    // Viewport left border (VS Code style)
    drawRect(
        color = Color(0xFF569CD6),
        topLeft = Offset(0f, viewportY),
        size = Size(2f, viewportHeight)
    )
}

/**
 * 将终端颜色转换为 Compose Color
 * 正确处理所有颜色类型：Default, Indexed (0-255), TrueColor (24-bit RGB)
 */
private fun getTerminalColor(terminalColor: TerminalColor, colorScheme: TerminalColorScheme): Color? {
    return when (terminalColor) {
        is TerminalColor.Default -> null
        is TerminalColor.Indexed -> getIndexedColor(terminalColor.index, colorScheme)
        is TerminalColor.TrueColor -> Color(
            red = terminalColor.r / 255f,
            green = terminalColor.g / 255f,
            blue = terminalColor.b / 255f
        )
    }
}

/**
 * 获取索引颜色 (0-255)
 * 0-7: 标准颜色
 * 8-15: 高亮颜色
 * 16-231: 216色立方体 (6x6x6)
 * 232-255: 24级灰度
 */
private fun getIndexedColor(index: Int, colorScheme: TerminalColorScheme): Color {
    return when {
        // 0-15: Use color scheme's 16 colors
        index < 16 -> colorScheme.colors.getOrElse(index) { colorScheme.foreground }

        // 16-231: 6x6x6 color cube
        index < 232 -> {
            val adjusted = index - 16
            val r = (adjusted / 36) % 6
            val g = (adjusted / 6) % 6
            val b = adjusted % 6
            // Convert 0-5 to 0-255 (0, 95, 135, 175, 215, 255)
            val rValue = if (r == 0) 0 else 55 + r * 40
            val gValue = if (g == 0) 0 else 55 + g * 40
            val bValue = if (b == 0) 0 else 55 + b * 40
            Color(
                red = rValue / 255f,
                green = gValue / 255f,
                blue = bValue / 255f
            )
        }

        // 232-255: 24 level grayscale
        else -> {
            val gray = 8 + (index - 232) * 10
            Color(
                red = gray / 255f,
                green = gray / 255f,
                blue = gray / 255f
            )
        }
    }
}
