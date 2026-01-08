package com.vibe.terminal.terminal.emulator

/**
 * 终端单元格
 * character 使用 String 以支持 Unicode 补充平面字符（如 Nerd Font 图标）
 */
data class TerminalCell(
    val character: String = " ",
    val attribute: CharacterAttribute = CharacterAttribute.DEFAULT,
    val width: Int = 1  // 1 for normal, 2 for wide characters (CJK)
) {
    companion object {
        val EMPTY = TerminalCell()
    }
}

/**
 * 终端行
 */
class TerminalRow(private val columns: Int) {
    private val cells = Array(columns) { TerminalCell.EMPTY }

    operator fun get(col: Int): TerminalCell {
        return if (col in 0 until columns) cells[col] else TerminalCell.EMPTY
    }

    operator fun set(col: Int, cell: TerminalCell) {
        if (col in 0 until columns) {
            cells[col] = cell
        }
    }

    fun clear(attribute: CharacterAttribute = CharacterAttribute.DEFAULT) {
        for (i in 0 until columns) {
            cells[i] = TerminalCell(" ", attribute)
        }
    }

    fun clearRange(start: Int, end: Int, attribute: CharacterAttribute = CharacterAttribute.DEFAULT) {
        for (i in start until minOf(end, columns)) {
            cells[i] = TerminalCell(" ", attribute)
        }
    }

    fun copyFrom(other: TerminalRow) {
        for (i in 0 until minOf(columns, other.columns)) {
            cells[i] = other.cells[i]
        }
    }
}
