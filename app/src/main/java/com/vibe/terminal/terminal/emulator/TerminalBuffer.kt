package com.vibe.terminal.terminal.emulator

/**
 * 终端缓冲区
 *
 * 管理屏幕内容和滚动历史
 */
class TerminalBuffer(
    var columns: Int,
    var rows: Int,
    private val maxScrollback: Int = 10000
) {
    // 当前屏幕行
    private val screenLines = mutableListOf<TerminalRow>()

    // 滚动历史
    private val scrollbackBuffer = ArrayDeque<TerminalRow>(maxScrollback)

    // 光标位置 (0-based)
    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set

    // 当前字符属性
    var currentAttribute: CharacterAttribute = CharacterAttribute.DEFAULT

    // 滚动区域
    var scrollTop: Int = 0
        private set
    var scrollBottom: Int = rows - 1
        private set

    init {
        initializeScreen()
    }

    private fun initializeScreen() {
        screenLines.clear()
        for (i in 0 until rows) {
            screenLines.add(TerminalRow(columns))
        }
    }

    /**
     * 获取指定位置的单元格
     */
    fun getCell(row: Int, col: Int): TerminalCell {
        return if (row in 0 until rows && col in 0 until columns) {
            screenLines[row][col]
        } else {
            TerminalCell.EMPTY
        }
    }

    /**
     * 设置指定位置的单元格
     */
    fun setCell(row: Int, col: Int, cell: TerminalCell) {
        if (row in 0 until rows && col in 0 until columns) {
            screenLines[row][col] = cell
        }
    }

    /**
     * 在当前光标位置写入字符
     */
    fun writeChar(char: Char) {
        if (cursorCol >= columns) {
            // 自动换行
            cursorCol = 0
            lineFeed()
        }

        setCell(cursorRow, cursorCol, TerminalCell(char, currentAttribute))
        cursorCol++
    }

    /**
     * 移动光标
     */
    fun setCursor(row: Int, col: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorCol = col.coerceIn(0, columns - 1)
    }

    /**
     * 相对移动光标
     */
    fun moveCursor(deltaRow: Int, deltaCol: Int) {
        setCursor(cursorRow + deltaRow, cursorCol + deltaCol)
    }

    /**
     * 换行
     */
    fun lineFeed() {
        if (cursorRow == scrollBottom) {
            scrollUp(1)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    /**
     * 回车
     */
    fun carriageReturn() {
        cursorCol = 0
    }

    /**
     * 向上滚动
     */
    fun scrollUp(lines: Int = 1) {
        repeat(lines) {
            if (scrollbackBuffer.size >= maxScrollback) {
                scrollbackBuffer.removeFirst()
            }
            scrollbackBuffer.addLast(screenLines[scrollTop])

            // 移动屏幕行
            for (i in scrollTop until scrollBottom) {
                screenLines[i] = screenLines[i + 1]
            }
            screenLines[scrollBottom] = TerminalRow(columns)
        }
    }

    /**
     * 向下滚动
     */
    fun scrollDown(lines: Int = 1) {
        repeat(lines) {
            for (i in scrollBottom downTo scrollTop + 1) {
                screenLines[i] = screenLines[i - 1]
            }
            screenLines[scrollTop] = TerminalRow(columns)
        }
    }

    /**
     * 清除屏幕
     */
    fun clearScreen(mode: ClearMode = ClearMode.ALL) {
        when (mode) {
            ClearMode.TO_END -> {
                // 清除从光标到屏幕末尾
                screenLines[cursorRow].clearRange(cursorCol, columns, currentAttribute)
                for (i in cursorRow + 1 until rows) {
                    screenLines[i].clear(currentAttribute)
                }
            }
            ClearMode.TO_START -> {
                // 清除从屏幕开始到光标
                for (i in 0 until cursorRow) {
                    screenLines[i].clear(currentAttribute)
                }
                screenLines[cursorRow].clearRange(0, cursorCol + 1, currentAttribute)
            }
            ClearMode.ALL -> {
                // 清除整个屏幕
                for (i in 0 until rows) {
                    screenLines[i].clear(currentAttribute)
                }
            }
        }
    }

    /**
     * 清除行
     */
    fun clearLine(mode: ClearMode = ClearMode.ALL) {
        when (mode) {
            ClearMode.TO_END -> screenLines[cursorRow].clearRange(cursorCol, columns, currentAttribute)
            ClearMode.TO_START -> screenLines[cursorRow].clearRange(0, cursorCol + 1, currentAttribute)
            ClearMode.ALL -> screenLines[cursorRow].clear(currentAttribute)
        }
    }

    /**
     * 设置滚动区域
     */
    fun setScrollRegion(top: Int, bottom: Int) {
        scrollTop = top.coerceIn(0, rows - 1)
        scrollBottom = bottom.coerceIn(scrollTop, rows - 1)
    }

    /**
     * 调整大小
     */
    fun resize(newColumns: Int, newRows: Int) {
        val oldScreenLines = screenLines.toList()

        columns = newColumns
        rows = newRows
        scrollTop = 0
        scrollBottom = newRows - 1

        screenLines.clear()
        for (i in 0 until newRows) {
            val newRow = TerminalRow(newColumns)
            if (i < oldScreenLines.size) {
                newRow.copyFrom(oldScreenLines[i])
            }
            screenLines.add(newRow)
        }

        // 调整光标位置
        cursorRow = cursorRow.coerceIn(0, newRows - 1)
        cursorCol = cursorCol.coerceIn(0, newColumns - 1)
    }

    /**
     * 获取滚动历史行数
     */
    fun getScrollbackSize(): Int = scrollbackBuffer.size

    /**
     * 获取滚动历史中的行
     */
    fun getScrollbackLine(index: Int): TerminalRow? {
        return if (index in 0 until scrollbackBuffer.size) {
            scrollbackBuffer[index]
        } else {
            null
        }
    }

    enum class ClearMode {
        TO_END,   // 从光标到末尾
        TO_START, // 从开始到光标
        ALL       // 全部
    }
}
