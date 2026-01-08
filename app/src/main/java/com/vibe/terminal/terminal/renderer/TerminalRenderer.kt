package com.vibe.terminal.terminal.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
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
import com.vibe.terminal.data.preferences.UserPreferences
import com.vibe.terminal.terminal.emulator.TerminalColor
import com.vibe.terminal.terminal.emulator.TerminalEmulator
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Terminal font family with Nerd Font support
 * Uses Iosevka Nerd Font for icons and symbols
 */
val IosevkaFontFamily = FontFamily(
    Font(R.font.iosevka_nerd, FontWeight.Normal),
    Font(R.font.iosevka_nerd_bold, FontWeight.Bold)
)

/**
 * JetBrains Mono Nerd Font family
 * Popular coding font with Nerd Font icons
 */
val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

/**
 * Legacy alias for backward compatibility
 */
val TerminalFontFamily = IosevkaFontFamily

/**
 * Get the font family based on user preference
 */
fun getTerminalFontFamily(fontPreference: String): FontFamily {
    return when (fontPreference) {
        UserPreferences.FONT_JETBRAINS_MONO -> JetBrainsMonoFontFamily
        UserPreferences.FONT_SYSTEM_MONO -> FontFamily.Monospace
        else -> IosevkaFontFamily
    }
}

/**
 * Check if a character string is a wide character (CJK, fullwidth, etc.)
 * Wide characters typically occupy 2 cells in terminal
 */
private fun isWideChar(char: String): Boolean {
    if (char.isEmpty()) return false
    val code = char.codePointAt(0)
    return when {
        // CJK Unified Ideographs
        code in 0x4E00..0x9FFF -> true
        // CJK Unified Ideographs Extension A
        code in 0x3400..0x4DBF -> true
        // CJK Unified Ideographs Extension B-F
        code in 0x20000..0x2FA1F -> true
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
 * Other fonts have good monospace coverage for ASCII/symbols/Nerd Font icons
 */
private fun getFontForChar(char: String, baseFontFamily: FontFamily): FontFamily {
    return if (isWideChar(char)) {
        FontFamily.Default  // CJK uses system font
    } else {
        baseFontFamily  // Use selected font for other characters (including Nerd Font icons)
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
    fontFamily: FontFamily = IosevkaFontFamily,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onPinchIn: () -> Unit = {},    // Zoom in (show single panel)
    onPinchOut: () -> Unit = {},   // Zoom out (show all panels)
    onSwipeLeft: () -> Unit = {},  // Next panel (horizontal)
    onSwipeRight: () -> Unit = {}, // Previous panel (horizontal)
    onSwipeUp: () -> Unit = {},    // Focus panel above
    onSwipeDown: () -> Unit = {},  // Focus panel below
    onTwoFingerSwipeLeft: () -> Unit = {},  // Next tab
    onTwoFingerSwipeRight: () -> Unit = {}, // Previous tab
    onThreeFingerTap: () -> Unit = {},      // Toggle floating panes
    onSendInput: (String) -> Unit = {}  // 用于发送方向键等输入
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // 计算字符尺寸
    val charSize = remember(fontSize, fontFamily) {
        with(density) {
            val style = TextStyle(
                fontFamily = fontFamily,
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

    // 滚动累积量
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // Swipe gesture tracking
    var swipeStartX by remember { mutableFloatStateOf(0f) }
    var swipeStartY by remember { mutableFloatStateOf(0f) }
    var isSwipeGesture by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            // Multi-finger gesture detection (pinch, two-finger swipe, three-finger tap)
            .pointerInput(Unit) {
                awaitEachGesture {
                    var initialDistance = 0f
                    var hasPinched = false
                    var hasTwoFingerSwiped = false
                    var twoFingerStartX = 0f
                    var hasThreeFingerTapped = false

                    awaitFirstDown()

                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }

                        // Three-finger tap detection
                        if (pointers.size == 3 && !hasThreeFingerTapped) {
                            hasThreeFingerTapped = true
                            onThreeFingerTap()
                            pointers.forEach { it.consume() }
                        }

                        if (pointers.size == 2) {
                            val pos1 = pointers[0].position
                            val pos2 = pointers[1].position
                            val currentDistance = sqrt(
                                (pos2.x - pos1.x) * (pos2.x - pos1.x) +
                                (pos2.y - pos1.y) * (pos2.y - pos1.y)
                            )
                            val centerX = (pos1.x + pos2.x) / 2

                            if (initialDistance == 0f) {
                                initialDistance = currentDistance
                                twoFingerStartX = centerX
                            } else if (!hasPinched && !hasTwoFingerSwiped) {
                                val pinchRatio = currentDistance / initialDistance
                                val horizontalDrag = centerX - twoFingerStartX

                                // Check for two-finger horizontal swipe first
                                if (abs(horizontalDrag) > 150 && abs(pinchRatio - 1.0f) < 0.3f) {
                                    hasTwoFingerSwiped = true
                                    if (horizontalDrag > 0) {
                                        onTwoFingerSwipeRight()  // Previous tab
                                    } else {
                                        onTwoFingerSwipeLeft()   // Next tab
                                    }
                                    pointers.forEach { it.consume() }
                                }
                                // Pinch in (zoom out, show all panels) - fingers move apart
                                else if (pinchRatio > 1.5f) {
                                    onPinchOut()
                                    hasPinched = true
                                    pointers.forEach { it.consume() }
                                }
                                // Pinch out (zoom in, show single panel) - fingers move together
                                else if (pinchRatio < 0.67f) {
                                    onPinchIn()
                                    hasPinched = true
                                    pointers.forEach { it.consume() }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            // Single finger swipe gesture detection
            .pointerInput(charSize) {
                detectDragGestures(
                    onDragStart = { offset ->
                        swipeStartX = offset.x
                        swipeStartY = offset.y
                        isSwipeGesture = false
                        dragAccumulator = 0f
                    },
                    onDragEnd = {
                        dragAccumulator = 0f
                        isSwipeGesture = false
                    },
                    onDrag = { change, dragAmount ->
                        val totalDragX = change.position.x - swipeStartX
                        val totalDragY = change.position.y - swipeStartY

                        // Check if this is a horizontal swipe (more horizontal than vertical)
                        if (abs(totalDragX) > abs(totalDragY) * 2 && abs(totalDragX) > 150) {
                            if (!isSwipeGesture) {
                                isSwipeGesture = true
                                change.consume()
                                if (totalDragX > 0) {
                                    onSwipeRight()  // Swipe right -> previous panel
                                } else {
                                    onSwipeLeft()   // Swipe left -> next panel
                                }
                            }
                        }
                        // Check if this is a vertical swipe (more vertical than horizontal)
                        else if (abs(totalDragY) > abs(totalDragX) * 2 && abs(totalDragY) > 200) {
                            if (!isSwipeGesture) {
                                isSwipeGesture = true
                                change.consume()
                                if (totalDragY > 0) {
                                    onSwipeDown()  // Swipe down -> focus panel below
                                } else {
                                    onSwipeUp()    // Swipe up -> focus panel above
                                }
                            }
                        } else if (!isSwipeGesture) {
                            // Vertical scroll handling (small movements)
                            change.consume()
                            // 累积垂直拖动距离
                            dragAccumulator += dragAmount.y

                            // 每移动2行高度，触发一次滚动
                            val lineHeight = charSize.height
                            val scrollThreshold = lineHeight * 2
                            if (lineHeight > 0 && scrollThreshold > 0) {
                                val scrollCount = (dragAccumulator / scrollThreshold).toInt()
                                if (scrollCount != 0) {
                                    if (emulator.isAlternateScreenMode) {
                                        // 在 alternate screen mode 中，发送鼠标滚轮事件
                                        val wheelButton = if (scrollCount > 0) 64 else 65
                                        repeat(kotlin.math.abs(scrollCount)) {
                                            onSendInput("\u001b[<$wheelButton;40;12M")
                                        }
                                    } else {
                                        // 普通模式下滚动历史缓冲区
                                        emulator.scrollView(scrollCount * 2)
                                    }
                                    dragAccumulator -= scrollCount * scrollThreshold
                                }
                            }
                        }
                    }
                )
            }
            // Tap and double-tap gesture detection
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (emulator.isScrolledBack) {
                            emulator.resetScroll()
                        }
                        onTap()
                    },
                    onDoubleTap = {
                        onDoubleTap()
                    }
                )
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
                fontFamily = fontFamily,
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
    fontFamily: FontFamily,
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
            if (cell.character.isNotEmpty() && cell.character != " " && cell.character != "\u0000") {
                // 只在有足够空间时绘制文本
                val availableWidth = canvasWidth - x
                if (availableWidth > 0) {
                    val fgColor = when (val fg = cell.attribute.foregroundColor) {
                        is TerminalColor.Default -> colorScheme.foreground
                        is TerminalColor.Indexed -> getIndexedColor(fg.index, colorScheme)
                        is TerminalColor.TrueColor -> Color(fg.r, fg.g, fg.b)
                    }

                    // 根据字符类型选择字体
                    val charFontFamily = getFontForChar(cell.character, fontFamily)
                    val isWide = isWideChar(cell.character)

                    val style = TextStyle(
                        fontFamily = charFontFamily,
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
                        text = cell.character,
                        style = style,
                        topLeft = Offset(x, y)
                    )
                }
            }
        }
    }

    // 绘制光标 (只在没有滚动查看历史时显示)
    if (cursorVisible && !emulator.isScrolledBack) {
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
