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
    val lineHeight = (canvasHeight / screenRows).coerceAtLeast(1f)
    val displayCols = cols.coerceAtMost(120) // Allow more columns for wider panels
    val charWidth = canvasWidth / displayCols

    // Draw only current screen content (no scrollback in alternate mode)
    for (row in 0 until screenRows) {
        val y = row * lineHeight
        if (y >= canvasHeight) break

        // Sample columns to represent the line
        for (colIdx in 0 until displayCols step 2) {
            val x = colIdx * charWidth
            // 直接获取屏幕上的 cell，不通过 scrollback
            val cell = emulator.getCell(row, colIdx)

            if (cell.character.isNotBlank() && cell.character != " " && cell.character != "\u0000") {
                val fgColor = when (val fg = cell.attribute.foregroundColor) {
                    is TerminalColor.Default -> colorScheme.foreground
                    is TerminalColor.Indexed -> getMinimapColor(fg.index, colorScheme)
                    is TerminalColor.TrueColor -> Color(fg.r, fg.g, fg.b)
                }

                // Draw a small rect representing the character
                drawRect(
                    color = fgColor.copy(alpha = 0.7f),
                    topLeft = Offset(x, y),
                    size = Size(charWidth * 2, lineHeight.coerceAtLeast(1f))
                )
            }
        }
    }

    // Draw a subtle border to indicate this is the current panel view
    drawRect(
        color = Color(0xFF569CD6).copy(alpha = 0.3f),
        topLeft = Offset(0f, 0f),
        size = Size(3f, canvasHeight)
    )
}

/**
 * 普通模式下渲染 minimap (包括 scrollback)
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
    val displayCols = cols.coerceAtMost(80) // Limit columns for readability
    val charWidth = canvasWidth / displayCols

    // Draw all lines (scrollback + current screen)
    for (lineIdx in 0 until totalLines) {
        val y = lineIdx * lineHeight
        if (y >= canvasHeight) break

        // Sample columns to represent the line (skip every other for performance)
        for (colIdx in 0 until displayCols step 2) {
            val x = colIdx * charWidth
            val cell = emulator.getCellAbsolute(lineIdx, colIdx)

            if (cell.character.isNotBlank() && cell.character != " " && cell.character != "\u0000") {
                val fgColor = when (val fg = cell.attribute.foregroundColor) {
                    is TerminalColor.Default -> colorScheme.foreground
                    is TerminalColor.Indexed -> getMinimapColor(fg.index, colorScheme)
                    is TerminalColor.TrueColor -> Color(fg.r, fg.g, fg.b)
                }

                // Draw a small rect representing the character
                drawRect(
                    color = fgColor.copy(alpha = 0.6f),
                    topLeft = Offset(x, y),
                    size = Size(charWidth * 2, lineHeight.coerceAtLeast(1f))
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

private fun getMinimapColor(index: Int, colorScheme: TerminalColorScheme): Color {
    return when {
        index < 16 -> colorScheme.colors.getOrElse(index) { colorScheme.foreground }
        index < 232 -> {
            val adjusted = index - 16
            val r = (adjusted / 36) * 51
            val g = ((adjusted / 6) % 6) * 51
            val b = (adjusted % 6) * 51
            Color(r, g, b)
        }
        else -> {
            val gray = (index - 232) * 10 + 8
            Color(gray, gray, gray)
        }
    }
}
