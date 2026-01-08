package com.vibe.terminal.terminal.parser

/**
 * ANSI转义序列解析器
 *
 * 支持VT100/xterm兼容的转义序列
 */
class AnsiParser(
    private val handler: AnsiHandler
) {
    private var state: State = State.GROUND
    private val params = mutableListOf<Int>()
    private val intermediates = StringBuilder()
    private val oscData = StringBuilder()

    // UTF-8 decoding state
    private var utf8State = Utf8State.START
    private var utf8Codepoint = 0
    private var utf8BytesRemaining = 0

    /**
     * 处理输入数据
     */
    fun process(data: ByteArray) {
        for (byte in data) {
            processByte(byte.toInt() and 0xFF)
        }
    }

    fun process(char: Char) {
        processByte(char.code)
    }

    private fun processByte(byte: Int) {
        // Handle UTF-8 multi-byte sequences in GROUND state
        if (state == State.GROUND) {
            when (utf8State) {
                Utf8State.START -> {
                    when {
                        // ASCII control characters and printable chars
                        byte < 0x80 -> handleGround(byte)
                        // UTF-8 2-byte sequence (110xxxxx)
                        byte in 0xC0..0xDF -> {
                            utf8Codepoint = byte and 0x1F
                            utf8BytesRemaining = 1
                            utf8State = Utf8State.CONTINUE
                        }
                        // UTF-8 3-byte sequence (1110xxxx)
                        byte in 0xE0..0xEF -> {
                            utf8Codepoint = byte and 0x0F
                            utf8BytesRemaining = 2
                            utf8State = Utf8State.CONTINUE
                        }
                        // UTF-8 4-byte sequence (11110xxx)
                        byte in 0xF0..0xF7 -> {
                            utf8Codepoint = byte and 0x07
                            utf8BytesRemaining = 3
                            utf8State = Utf8State.CONTINUE
                        }
                        // Invalid UTF-8 lead byte or continuation byte without lead
                        else -> {
                            // Treat as Latin-1 for compatibility
                            handler.printCodePoint(byte)
                        }
                    }
                }
                Utf8State.CONTINUE -> {
                    if (byte in 0x80..0xBF) {
                        // Valid continuation byte (10xxxxxx)
                        utf8Codepoint = (utf8Codepoint shl 6) or (byte and 0x3F)
                        utf8BytesRemaining--
                        if (utf8BytesRemaining == 0) {
                            // Complete UTF-8 sequence
                            emitCodepoint(utf8Codepoint)
                            utf8State = Utf8State.START
                            utf8Codepoint = 0
                        }
                    } else {
                        // Invalid continuation - reset and reprocess
                        utf8State = Utf8State.START
                        utf8Codepoint = 0
                        utf8BytesRemaining = 0
                        processByte(byte)
                    }
                }
            }
        } else {
            // In escape sequence states, handle bytes directly
            handleEscapeSequence(byte)
        }
    }

    private fun emitCodepoint(codepoint: Int) {
        handler.printCodePoint(codepoint)
    }

    private fun handleEscapeSequence(byte: Int) {
        when (state) {
            State.GROUND -> { /* handled above */ }
            State.ESCAPE -> handleEscape(byte)
            State.CSI_ENTRY -> handleCsiEntry(byte)
            State.CSI_PARAM -> handleCsiParam(byte)
            State.CSI_INTERMEDIATE -> handleCsiIntermediate(byte)
            State.OSC_STRING -> handleOscString(byte)
        }
    }

    private fun handleGround(byte: Int) {
        when (byte) {
            0x1B -> { // ESC
                state = State.ESCAPE
            }
            0x07 -> handler.bell()
            0x08 -> handler.backspace()
            0x09 -> handler.tab()
            0x0A, 0x0B, 0x0C -> handler.lineFeed()
            0x0D -> handler.carriageReturn()
            in 0x20..0x7E -> handler.printCodePoint(byte)
        }
    }

    private fun handleEscape(byte: Int) {
        when (byte) {
            0x5B -> { // [
                state = State.CSI_ENTRY
                params.clear()
                intermediates.clear()
            }
            0x5D -> { // ]
                state = State.OSC_STRING
                oscData.clear()
            }
            0x37 -> { // 7 - Save cursor
                handler.saveCursor()
                state = State.GROUND
            }
            0x38 -> { // 8 - Restore cursor
                handler.restoreCursor()
                state = State.GROUND
            }
            0x44 -> { // D - Index (move down)
                handler.index()
                state = State.GROUND
            }
            0x4D -> { // M - Reverse index (move up)
                handler.reverseIndex()
                state = State.GROUND
            }
            0x45 -> { // E - Next line
                handler.nextLine()
                state = State.GROUND
            }
            0x63 -> { // c - Reset
                handler.reset()
                state = State.GROUND
            }
            else -> state = State.GROUND
        }
    }

    private fun handleCsiEntry(byte: Int) {
        when (byte) {
            in 0x30..0x39 -> { // 0-9
                params.add(byte - 0x30)
                state = State.CSI_PARAM
            }
            0x3B -> { // ;
                params.add(0)
                state = State.CSI_PARAM
            }
            0x3F -> { // ?
                intermediates.append('?')
                state = State.CSI_PARAM
            }
            in 0x40..0x7E -> { // Final byte
                executeCsi(byte.toChar())
                state = State.GROUND
            }
            else -> state = State.GROUND
        }
    }

    private fun handleCsiParam(byte: Int) {
        when (byte) {
            in 0x30..0x39 -> { // 0-9
                val lastIndex = params.lastIndex
                if (lastIndex >= 0) {
                    params[lastIndex] = params[lastIndex] * 10 + (byte - 0x30)
                } else {
                    params.add(byte - 0x30)
                }
            }
            0x3B -> { // ;
                params.add(0)
            }
            in 0x20..0x2F -> { // Intermediate bytes
                intermediates.append(byte.toChar())
                state = State.CSI_INTERMEDIATE
            }
            in 0x40..0x7E -> { // Final byte
                executeCsi(byte.toChar())
                state = State.GROUND
            }
            else -> state = State.GROUND
        }
    }

    private fun handleCsiIntermediate(byte: Int) {
        when (byte) {
            in 0x20..0x2F -> intermediates.append(byte.toChar())
            in 0x40..0x7E -> {
                executeCsi(byte.toChar())
                state = State.GROUND
            }
            else -> state = State.GROUND
        }
    }

    private fun handleOscString(byte: Int) {
        when (byte) {
            0x07 -> { // BEL - String terminator
                executeOsc()
                state = State.GROUND
            }
            0x1B -> { // ESC - Might be ST
                // Wait for next char
            }
            0x5C -> { // \ - ST if preceded by ESC
                executeOsc()
                state = State.GROUND
            }
            else -> oscData.append(byte.toChar())
        }
    }

    private fun executeCsi(finalByte: Char) {
        val isPrivate = intermediates.startsWith("?")

        when (finalByte) {
            'A' -> handler.cursorUp(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'B' -> handler.cursorDown(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'C' -> handler.cursorForward(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'D' -> handler.cursorBack(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'E' -> handler.cursorNextLine(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'F' -> handler.cursorPrevLine(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'G' -> handler.cursorColumn(params.getOrElse(0) { 1 })
            'H', 'f' -> handler.cursorPosition(
                params.getOrElse(0) { 1 },
                params.getOrElse(1) { 1 }
            )
            'J' -> handler.eraseDisplay(params.getOrElse(0) { 0 })
            'K' -> handler.eraseLine(params.getOrElse(0) { 0 })
            'L' -> handler.insertLines(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'M' -> handler.deleteLines(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'P' -> handler.deleteChars(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'S' -> handler.scrollUp(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'T' -> handler.scrollDown(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'X' -> handler.eraseChars(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            '@' -> handler.insertChars(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            'd' -> handler.cursorRow(params.getOrElse(0) { 1 })
            'm' -> handleSgr()
            'r' -> handler.setScrollRegion(
                params.getOrElse(0) { 1 },
                params.getOrElse(1) { 0 }
            )
            's' -> handler.saveCursor()
            'u' -> handler.restoreCursor()
            'h' -> {
                if (isPrivate) {
                    params.forEach { handler.setPrivateMode(it, true) }
                } else {
                    params.forEach { handler.setMode(it, true) }
                }
            }
            'l' -> {
                if (isPrivate) {
                    params.forEach { handler.setPrivateMode(it, false) }
                } else {
                    params.forEach { handler.setMode(it, false) }
                }
            }
            'c' -> handler.sendDeviceAttributes()
            'n' -> handler.deviceStatusReport(params.getOrElse(0) { 0 })
        }
    }

    private fun handleSgr() {
        if (params.isEmpty()) {
            handler.setGraphicsRendition(listOf(0))
            return
        }

        val processedParams = mutableListOf<Int>()
        var i = 0

        while (i < params.size) {
            when (params[i]) {
                38 -> { // Foreground color
                    if (i + 1 < params.size && params[i + 1] == 5) {
                        // 256 color mode: 38;5;n
                        if (i + 2 < params.size) {
                            processedParams.add(38)
                            processedParams.add(5)
                            processedParams.add(params[i + 2])
                            i += 3
                            continue
                        }
                    } else if (i + 1 < params.size && params[i + 1] == 2) {
                        // True color mode: 38;2;r;g;b
                        if (i + 4 < params.size) {
                            processedParams.add(38)
                            processedParams.add(2)
                            processedParams.add(params[i + 2])
                            processedParams.add(params[i + 3])
                            processedParams.add(params[i + 4])
                            i += 5
                            continue
                        }
                    }
                }
                48 -> { // Background color
                    if (i + 1 < params.size && params[i + 1] == 5) {
                        if (i + 2 < params.size) {
                            processedParams.add(48)
                            processedParams.add(5)
                            processedParams.add(params[i + 2])
                            i += 3
                            continue
                        }
                    } else if (i + 1 < params.size && params[i + 1] == 2) {
                        if (i + 4 < params.size) {
                            processedParams.add(48)
                            processedParams.add(2)
                            processedParams.add(params[i + 2])
                            processedParams.add(params[i + 3])
                            processedParams.add(params[i + 4])
                            i += 5
                            continue
                        }
                    }
                }
            }
            processedParams.add(params[i])
            i++
        }

        handler.setGraphicsRendition(processedParams)
    }

    private fun executeOsc() {
        val parts = oscData.toString().split(";", limit = 2)
        val command = parts.getOrNull(0)?.toIntOrNull() ?: return
        val data = parts.getOrNull(1) ?: ""

        handler.operatingSystemCommand(command, data)
    }

    private enum class State {
        GROUND,
        ESCAPE,
        CSI_ENTRY,
        CSI_PARAM,
        CSI_INTERMEDIATE,
        OSC_STRING
    }

    private enum class Utf8State {
        START,
        CONTINUE
    }
}

/**
 * ANSI序列处理接口
 */
interface AnsiHandler {
    // 打印字符 (使用 code point 以支持补充平面字符)
    fun printCodePoint(codePoint: Int)

    // 控制字符
    fun bell()
    fun backspace()
    fun tab()
    fun lineFeed()
    fun carriageReturn()

    // 光标移动
    fun cursorUp(n: Int)
    fun cursorDown(n: Int)
    fun cursorForward(n: Int)
    fun cursorBack(n: Int)
    fun cursorNextLine(n: Int)
    fun cursorPrevLine(n: Int)
    fun cursorColumn(col: Int)
    fun cursorRow(row: Int)
    fun cursorPosition(row: Int, col: Int)

    // 光标保存/恢复
    fun saveCursor()
    fun restoreCursor()

    // 清除
    fun eraseDisplay(mode: Int)
    fun eraseLine(mode: Int)
    fun eraseChars(n: Int)

    // 插入/删除
    fun insertLines(n: Int)
    fun deleteLines(n: Int)
    fun insertChars(n: Int)
    fun deleteChars(n: Int)

    // 滚动
    fun scrollUp(n: Int)
    fun scrollDown(n: Int)
    fun setScrollRegion(top: Int, bottom: Int)
    fun index()
    fun reverseIndex()
    fun nextLine()

    // 属性
    fun setGraphicsRendition(params: List<Int>)

    // 模式
    fun setMode(mode: Int, enabled: Boolean)
    fun setPrivateMode(mode: Int, enabled: Boolean)

    // 设备
    fun sendDeviceAttributes()
    fun deviceStatusReport(type: Int)

    // OSC
    fun operatingSystemCommand(command: Int, data: String)

    // 重置
    fun reset()
}
