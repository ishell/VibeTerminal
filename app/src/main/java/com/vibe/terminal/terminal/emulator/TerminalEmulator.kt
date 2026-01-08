package com.vibe.terminal.terminal.emulator

import com.vibe.terminal.terminal.parser.AnsiHandler
import com.vibe.terminal.terminal.parser.AnsiParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 终端模拟器
 *
 * 实现VT100/xterm兼容的终端模拟
 */
class TerminalEmulator(
    columns: Int = 80,
    rows: Int = 24
) : AnsiHandler {

    private val buffer = TerminalBuffer(columns, rows)
    private val parser = AnsiParser(this)

    // 保存的光标状态
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var savedAttribute = CharacterAttribute.DEFAULT

    // 终端标题
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    // 内容更新信号
    private val _updateSignal = MutableStateFlow(0L)
    val updateSignal: StateFlow<Long> = _updateSignal.asStateFlow()

    // 视图滚动偏移 (0 = 底部/当前屏幕, 正数 = 向上滚动查看历史)
    private var _scrollOffset = 0
    val scrollOffset: Int get() = _scrollOffset
    val maxScrollOffset: Int get() = buffer.getScrollbackSize()

    // Alternate screen mode (用于 vim, zellij, tmux 等全屏应用)
    private var _alternateScreenMode = false
    val isAlternateScreenMode: Boolean get() = _alternateScreenMode

    // 响应数据 (需要发送回服务器的)
    private val _responseData = MutableStateFlow<ByteArray?>(null)
    val responseData: StateFlow<ByteArray?> = _responseData.asStateFlow()

    val columns: Int get() = buffer.columns
    val rows: Int get() = buffer.rows
    val cursorRow: Int get() = buffer.cursorRow
    val cursorCol: Int get() = buffer.cursorCol

    /**
     * 处理输入数据
     */
    fun processInput(data: ByteArray) {
        synchronized(buffer) {
            parser.process(data)
        }
        notifyUpdate()
    }

    fun processInput(text: String) {
        processInput(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * 获取单元格 (考虑滚动偏移)
     */
    fun getCell(row: Int, col: Int): TerminalCell {
        return synchronized(buffer) {
            if (_scrollOffset == 0) {
                buffer.getCell(row, col)
            } else {
                // 计算实际行位置
                val scrollbackSize = buffer.getScrollbackSize()
                val actualRow = row - _scrollOffset

                if (actualRow < 0) {
                    // 在滚动历史中
                    val scrollbackIndex = scrollbackSize + actualRow
                    val scrollbackRow = buffer.getScrollbackLine(scrollbackIndex)
                    scrollbackRow?.get(col) ?: TerminalCell.EMPTY
                } else {
                    // 在当前屏幕中
                    buffer.getCell(actualRow, col)
                }
            }
        }
    }

    /**
     * 滚动视图 (用于手势滚动查看历史)
     * @param delta 正数向上滚动(查看历史), 负数向下滚动(回到当前)
     */
    fun scrollView(delta: Int) {
        synchronized(buffer) {
            val newOffset = (_scrollOffset + delta).coerceIn(0, buffer.getScrollbackSize())
            if (newOffset != _scrollOffset) {
                _scrollOffset = newOffset
                notifyUpdate()
            }
        }
    }

    /**
     * 重置滚动位置到底部
     */
    fun resetScroll() {
        if (_scrollOffset != 0) {
            _scrollOffset = 0
            notifyUpdate()
        }
    }

    /**
     * 是否正在查看历史
     */
    val isScrolledBack: Boolean get() = _scrollOffset > 0

    /**
     * 调整大小
     */
    fun resize(newColumns: Int, newRows: Int) {
        synchronized(buffer) {
            buffer.resize(newColumns, newRows)
        }
        notifyUpdate()
    }

    /**
     * 清除响应数据
     */
    fun clearResponseData() {
        _responseData.value = null
    }

    private fun notifyUpdate() {
        _updateSignal.value = System.currentTimeMillis()
    }

    private fun sendResponse(data: String) {
        _responseData.value = data.toByteArray(Charsets.UTF_8)
    }

    // ==================== AnsiHandler 实现 ====================

    override fun printCodePoint(codePoint: Int) {
        // Convert code point to String (handles supplementary characters automatically)
        val char = StringBuilder().appendCodePoint(codePoint).toString()
        buffer.writeChar(char)
    }

    override fun bell() {
        // TODO: 播放提示音或震动
    }

    override fun backspace() {
        if (buffer.cursorCol > 0) {
            buffer.moveCursor(0, -1)
        }
    }

    override fun tab() {
        val nextTab = ((buffer.cursorCol / 8) + 1) * 8
        buffer.setCursor(buffer.cursorRow, nextTab.coerceAtMost(buffer.columns - 1))
    }

    override fun lineFeed() {
        buffer.lineFeed()
    }

    override fun carriageReturn() {
        buffer.carriageReturn()
    }

    override fun cursorUp(n: Int) {
        buffer.moveCursor(-n, 0)
    }

    override fun cursorDown(n: Int) {
        buffer.moveCursor(n, 0)
    }

    override fun cursorForward(n: Int) {
        buffer.moveCursor(0, n)
    }

    override fun cursorBack(n: Int) {
        buffer.moveCursor(0, -n)
    }

    override fun cursorNextLine(n: Int) {
        buffer.moveCursor(n, 0)
        buffer.carriageReturn()
    }

    override fun cursorPrevLine(n: Int) {
        buffer.moveCursor(-n, 0)
        buffer.carriageReturn()
    }

    override fun cursorColumn(col: Int) {
        buffer.setCursor(buffer.cursorRow, col - 1)
    }

    override fun cursorRow(row: Int) {
        buffer.setCursor(row - 1, buffer.cursorCol)
    }

    override fun cursorPosition(row: Int, col: Int) {
        buffer.setCursor(row - 1, col - 1)
    }

    override fun saveCursor() {
        savedCursorRow = buffer.cursorRow
        savedCursorCol = buffer.cursorCol
        savedAttribute = buffer.currentAttribute
    }

    override fun restoreCursor() {
        buffer.setCursor(savedCursorRow, savedCursorCol)
        buffer.currentAttribute = savedAttribute
    }

    override fun eraseDisplay(mode: Int) {
        val clearMode = when (mode) {
            0 -> TerminalBuffer.ClearMode.TO_END
            1 -> TerminalBuffer.ClearMode.TO_START
            else -> TerminalBuffer.ClearMode.ALL
        }
        buffer.clearScreen(clearMode)
    }

    override fun eraseLine(mode: Int) {
        val clearMode = when (mode) {
            0 -> TerminalBuffer.ClearMode.TO_END
            1 -> TerminalBuffer.ClearMode.TO_START
            else -> TerminalBuffer.ClearMode.ALL
        }
        buffer.clearLine(clearMode)
    }

    override fun eraseChars(n: Int) {
        for (i in 0 until n) {
            val col = buffer.cursorCol + i
            if (col < buffer.columns) {
                buffer.setCell(buffer.cursorRow, col, TerminalCell(" ", buffer.currentAttribute))
            }
        }
    }

    override fun insertLines(n: Int) {
        // TODO: 实现插入行
    }

    override fun deleteLines(n: Int) {
        // TODO: 实现删除行
    }

    override fun insertChars(n: Int) {
        // TODO: 实现插入字符
    }

    override fun deleteChars(n: Int) {
        // TODO: 实现删除字符
    }

    override fun scrollUp(n: Int) {
        buffer.scrollUp(n)
    }

    override fun scrollDown(n: Int) {
        buffer.scrollDown(n)
    }

    override fun setScrollRegion(top: Int, bottom: Int) {
        val actualBottom = if (bottom == 0) buffer.rows else bottom
        buffer.setScrollRegion(top - 1, actualBottom - 1)
        buffer.setCursor(0, 0)
    }

    override fun index() {
        lineFeed()
    }

    override fun reverseIndex() {
        if (buffer.cursorRow == buffer.scrollTop) {
            buffer.scrollDown(1)
        } else if (buffer.cursorRow > 0) {
            buffer.moveCursor(-1, 0)
        }
    }

    override fun nextLine() {
        carriageReturn()
        lineFeed()
    }

    override fun setGraphicsRendition(params: List<Int>) {
        var attr = buffer.currentAttribute
        var i = 0

        while (i < params.size) {
            when (params[i]) {
                0 -> attr = CharacterAttribute.DEFAULT
                1 -> attr = attr.copy(bold = true)
                2 -> attr = attr.copy(dim = true)
                3 -> attr = attr.copy(italic = true)
                4 -> attr = attr.copy(underline = true)
                7 -> attr = attr.copy(inverse = true)
                8 -> attr = attr.copy(hidden = true)
                9 -> attr = attr.copy(strikethrough = true)
                22 -> attr = attr.copy(bold = false, dim = false)
                23 -> attr = attr.copy(italic = false)
                24 -> attr = attr.copy(underline = false)
                27 -> attr = attr.copy(inverse = false)
                28 -> attr = attr.copy(hidden = false)
                29 -> attr = attr.copy(strikethrough = false)
                in 30..37 -> attr = attr.copy(foregroundColor = TerminalColor.Indexed(params[i] - 30))
                38 -> {
                    if (i + 1 < params.size) {
                        when (params[i + 1]) {
                            5 -> {
                                if (i + 2 < params.size) {
                                    attr = attr.copy(foregroundColor = TerminalColor.Indexed(params[i + 2]))
                                    i += 2
                                }
                            }
                            2 -> {
                                if (i + 4 < params.size) {
                                    attr = attr.copy(
                                        foregroundColor = TerminalColor.TrueColor(
                                            params[i + 2],
                                            params[i + 3],
                                            params[i + 4]
                                        )
                                    )
                                    i += 4
                                }
                            }
                        }
                    }
                }
                39 -> attr = attr.copy(foregroundColor = TerminalColor.Default)
                in 40..47 -> attr = attr.copy(backgroundColor = TerminalColor.Indexed(params[i] - 40))
                48 -> {
                    if (i + 1 < params.size) {
                        when (params[i + 1]) {
                            5 -> {
                                if (i + 2 < params.size) {
                                    attr = attr.copy(backgroundColor = TerminalColor.Indexed(params[i + 2]))
                                    i += 2
                                }
                            }
                            2 -> {
                                if (i + 4 < params.size) {
                                    attr = attr.copy(
                                        backgroundColor = TerminalColor.TrueColor(
                                            params[i + 2],
                                            params[i + 3],
                                            params[i + 4]
                                        )
                                    )
                                    i += 4
                                }
                            }
                        }
                    }
                }
                49 -> attr = attr.copy(backgroundColor = TerminalColor.Default)
                in 90..97 -> attr = attr.copy(foregroundColor = TerminalColor.Indexed(params[i] - 90 + 8))
                in 100..107 -> attr = attr.copy(backgroundColor = TerminalColor.Indexed(params[i] - 100 + 8))
            }
            i++
        }

        buffer.currentAttribute = attr
    }

    override fun setMode(mode: Int, enabled: Boolean) {
        // TODO: 实现标准模式
    }

    override fun setPrivateMode(mode: Int, enabled: Boolean) {
        when (mode) {
            25 -> {
                // 光标显示/隐藏
            }
            47, 1047, 1049 -> {
                // 替代屏幕缓冲区
                _alternateScreenMode = enabled
                // 进入/退出 alternate screen 时重置滚动位置
                if (enabled) {
                    _scrollOffset = 0
                }
            }
        }
    }

    override fun sendDeviceAttributes() {
        sendResponse("\u001b[?1;2c")
    }

    override fun deviceStatusReport(type: Int) {
        when (type) {
            5 -> sendResponse("\u001b[0n") // 设备状态OK
            6 -> sendResponse("\u001b[${buffer.cursorRow + 1};${buffer.cursorCol + 1}R") // 光标位置
        }
    }

    override fun operatingSystemCommand(command: Int, data: String) {
        when (command) {
            0, 2 -> _title.value = data // 设置标题
            1 -> {} // 设置图标名
        }
    }

    override fun reset() {
        buffer.resize(buffer.columns, buffer.rows)
        buffer.currentAttribute = CharacterAttribute.DEFAULT
        savedCursorRow = 0
        savedCursorCol = 0
        savedAttribute = CharacterAttribute.DEFAULT
    }
}
