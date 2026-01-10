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
 * 使用更高分辨率渲染以获得更好的清晰度，包含彩色显示
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

    // Calculate pixel size for each cell in minimap
    // Use finer granularity for better resolution
    val lineHeight = (canvasHeight / screenRows).coerceAtLeast(1f)
    val charWidth = (canvasWidth / cols).coerceAtLeast(0.5f)

    // Draw current screen content with higher resolution and full color
    for (row in 0 until screenRows) {
        val y = row * lineHeight
        if (y >= canvasHeight) break

        // Render every column for better fidelity (no skipping)
        for (colIdx in 0 until cols) {
            val x = colIdx * charWidth
            val cell = emulator.getCell(row, colIdx)

            // Get background color first
            val bgColor = getTerminalColor(cell.attribute.backgroundColor, colorScheme)

            // Draw background if it's not the default background
            if (bgColor != null && bgColor != colorScheme.background) {
                drawRect(
                    color = bgColor,
                    topLeft = Offset(x, y),
                    size = Size(charWidth.coerceAtLeast(1f), lineHeight.coerceAtLeast(1f))
                )
            }

            // Draw foreground character if present
            if (cell.character.isNotBlank() && cell.character != " " && cell.character != "\u0000") {
                val fgColor = getTerminalColor(cell.attribute.foregroundColor, colorScheme)
                    ?: colorScheme.foreground

                drawRect(
                    color = fgColor,
                    topLeft = Offset(x, y),
                    size = Size(charWidth.coerceAtLeast(0.8f), lineHeight.coerceAtLeast(1f))
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
 * 针对大量历史记录进行性能优化，但保持较好的视觉效果和彩色显示
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

    // Calculate pixel size for each cell in minimap
    val lineHeight = (canvasHeight / totalLines).coerceAtLeast(0.5f)
    val charWidth = (canvasWidth / cols).coerceAtLeast(0.3f)

    // Determine sampling step based on total lines
    // For very long histories, sample more aggressively
    val lineStep = when {
        totalLines > 5000 -> 4
        totalLines > 2000 -> 2
        else -> 1
    }

    // Column sampling - render more columns for better detail
    val colStep = when {
        cols > 200 -> 2
        else -> 1
    }

    // Draw all lines (scrollback + current screen) with colors
    for (lineIdx in 0 until totalLines step lineStep) {
        val y = lineIdx * lineHeight
        if (y >= canvasHeight) break

        for (colIdx in 0 until cols step colStep) {
            val x = colIdx * charWidth
            val cell = emulator.getCellAbsolute(lineIdx, colIdx)

            val rectWidth = (charWidth * colStep).coerceAtLeast(0.8f)
            val rectHeight = (lineHeight * lineStep).coerceAtLeast(0.5f)

            // Get background color
            val bgColor = getTerminalColor(cell.attribute.backgroundColor, colorScheme)

            // Draw background if it's not the default background
            if (bgColor != null && bgColor != colorScheme.background) {
                drawRect(
                    color = bgColor,
                    topLeft = Offset(x, y),
                    size = Size(rectWidth, rectHeight)
                )
            }

            // Draw foreground character if present
            if (cell.character.isNotBlank() && cell.character != " " && cell.character != "\u0000") {
                val fgColor = getTerminalColor(cell.attribute.foregroundColor, colorScheme)
                    ?: colorScheme.foreground

                drawRect(
                    color = fgColor,
                    topLeft = Offset(x, y),
                    size = Size(rectWidth, rectHeight)
                )
            }
        }
    }

    // Draw viewport indicator (current visible area)
    // viewport starts at (scrollbackSize - scrollOffset) and spans screenRows
    val viewportStartLine = scrollbackSize - emulator.scrollOffset
    val viewportY = viewportStartLine * lineHeight
    val viewportHeight = screenRows * lineHeight

    // Viewport background highlight
    drawRect(
        color = Color.White.copy(alpha = 0.15f),
        topLeft = Offset(0f, viewportY),
        size = Size(canvasWidth, viewportHeight)
    )

    // Viewport left border (VS Code style)
    drawRect(
        color = Color(0xFF569CD6),
        topLeft = Offset(0f, viewportY),
        size = Size(3f, viewportHeight)
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
