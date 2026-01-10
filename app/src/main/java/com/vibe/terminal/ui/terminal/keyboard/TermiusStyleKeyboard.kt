package com.vibe.terminal.ui.terminal.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Termius-style keyboard bar colors
private val KeyboardBarBackground = Color(0xFF1E1E1E)
private val KeyActiveBackground = Color(0xFF3D3D3D)
private val KeyTextColor = Color(0xFFE0E0E0)
private val KeyAccentColor = Color(0xFF4FC3F7)
private val CtrlActiveColor = Color(0xFF4CAF50)
private val AltActiveColor = Color(0xFFFF9800)
private val ZellijColor = Color(0xFFAB47BC)

/**
 * Termius-style keyboard bar
 * Shows a row of function keys above the system keyboard
 * Supports Ctrl/Alt modifier modes and multiple key groups
 * Includes Zellij-specific shortcuts
 */
@Composable
fun TermiusStyleKeyboard(
    onKey: (Int) -> Unit,
    onCtrlKey: (Char) -> Unit,
    onSendInput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var currentGroup by remember { mutableIntStateOf(0) }

    val keyGroups = remember {
        listOf(
            // Common navigation keys
            KeyGroup("Nav", listOf(
                KeyDef("Esc", icon = null, action = { onKey(KEY_ESCAPE) }),
                KeyDef("Tab", icon = Icons.AutoMirrored.Filled.KeyboardTab, action = { onKey(KEY_TAB) }),
                KeyDef("↑", icon = Icons.Default.KeyboardArrowUp, action = { onKey(KEY_UP) }),
                KeyDef("↓", icon = Icons.Default.KeyboardArrowDown, action = { onKey(KEY_DOWN) }),
                KeyDef("←", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, action = { onKey(KEY_LEFT) }),
                KeyDef("→", icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, action = { onKey(KEY_RIGHT) }),
                KeyDef("PgUp", icon = null, action = { onSendInput("\u001b[5~") }),
                KeyDef("PgDn", icon = null, action = { onSendInput("\u001b[6~") }),
                KeyDef("Home", icon = Icons.Default.Home, action = { onSendInput("\u001b[H") }),
                KeyDef("End", icon = null, action = { onSendInput("\u001b[F") })
            )),
            // Zellij mode switches (Ctrl+key to enter mode)
            KeyGroup("Zellij", listOf(
                KeyDef("Pane", icon = Icons.Default.GridView, action = { onCtrlKey('P') }, accent = true, tooltip = "Ctrl+P"),
                KeyDef("Tab", icon = Icons.Default.Tab, action = { onCtrlKey('T') }, accent = true, tooltip = "Ctrl+T"),
                KeyDef("Resize", icon = Icons.Default.UnfoldMore, action = { onCtrlKey('N') }, tooltip = "Ctrl+N"),
                KeyDef("Move", icon = Icons.Default.SwapHoriz, action = { onCtrlKey('H') }, tooltip = "Ctrl+H"),
                KeyDef("Scroll", icon = Icons.Default.SwapVert, action = { onCtrlKey('S') }, tooltip = "Ctrl+S"),
                KeyDef("Session", icon = Icons.Default.OpenInNew, action = { onCtrlKey('O') }, tooltip = "Ctrl+O"),
                KeyDef("Lock", icon = Icons.Default.Lock, action = { onCtrlKey('G') }, tooltip = "Ctrl+G"),
                KeyDef("Full", icon = Icons.Default.Fullscreen, action = { onCtrlKey('F') }, tooltip = "Ctrl+F")
            )),
            // Zellij pane operations (when in pane mode)
            KeyGroup("Pane", listOf(
                KeyDef("New↓", icon = null, action = { onSendInput("n") }, tooltip = "New pane down"),
                KeyDef("New→", icon = null, action = { onSendInput("r") }, tooltip = "New pane right"),
                KeyDef("Close", icon = Icons.Default.Close, action = { onSendInput("x") }),
                KeyDef("Full", icon = Icons.Default.Fullscreen, action = { onSendInput("f") }),
                KeyDef("Float", icon = null, action = { onSendInput("w") }),
                KeyDef("Rename", icon = null, action = { onSendInput("c") }),
                KeyDef("←", icon = null, action = { onSendInput("h") }),
                KeyDef("↓", icon = null, action = { onSendInput("j") }),
                KeyDef("↑", icon = null, action = { onSendInput("k") }),
                KeyDef("→", icon = null, action = { onSendInput("l") })
            )),
            // Zellij tab operations (when in tab mode)
            KeyGroup("Tab", listOf(
                KeyDef("New", icon = Icons.Default.Add, action = { onSendInput("n") }),
                KeyDef("Close", icon = Icons.Default.Close, action = { onSendInput("x") }),
                KeyDef("Rename", icon = null, action = { onSendInput("r") }),
                KeyDef("Sync", icon = null, action = { onSendInput("s") }),
                KeyDef("Break", icon = null, action = { onSendInput("b") }),
                KeyDef("1", icon = null, action = { onSendInput("1") }),
                KeyDef("2", icon = null, action = { onSendInput("2") }),
                KeyDef("3", icon = null, action = { onSendInput("3") }),
                KeyDef("4", icon = null, action = { onSendInput("4") }),
                KeyDef("5", icon = null, action = { onSendInput("5") })
            )),
            // Common Ctrl shortcuts
            KeyGroup("Ctrl", listOf(
                KeyDef("^C", icon = null, action = { onCtrlKey('C') }, accent = true),
                KeyDef("^D", icon = null, action = { onCtrlKey('D') }, accent = true),
                KeyDef("^Z", icon = null, action = { onCtrlKey('Z') }),
                KeyDef("^L", icon = null, action = { onCtrlKey('L') }),
                KeyDef("^A", icon = null, action = { onCtrlKey('A') }),
                KeyDef("^E", icon = null, action = { onCtrlKey('E') }),
                KeyDef("^K", icon = null, action = { onCtrlKey('K') }),
                KeyDef("^U", icon = null, action = { onCtrlKey('U') }),
                KeyDef("^W", icon = null, action = { onCtrlKey('W') }),
                KeyDef("^R", icon = null, action = { onCtrlKey('R') })
            )),
            // Function keys
            KeyGroup("F-Keys", listOf(
                KeyDef("F1", icon = null, action = { onSendInput("\u001bOP") }),
                KeyDef("F2", icon = null, action = { onSendInput("\u001bOQ") }),
                KeyDef("F3", icon = null, action = { onSendInput("\u001bOR") }),
                KeyDef("F4", icon = null, action = { onSendInput("\u001bOS") }),
                KeyDef("F5", icon = null, action = { onSendInput("\u001b[15~") }),
                KeyDef("F6", icon = null, action = { onSendInput("\u001b[17~") }),
                KeyDef("F7", icon = null, action = { onSendInput("\u001b[18~") }),
                KeyDef("F8", icon = null, action = { onSendInput("\u001b[19~") }),
                KeyDef("F9", icon = null, action = { onSendInput("\u001b[20~") }),
                KeyDef("F10", icon = null, action = { onSendInput("\u001b[21~") }),
                KeyDef("F11", icon = null, action = { onSendInput("\u001b[23~") }),
                KeyDef("F12", icon = null, action = { onSendInput("\u001b[24~") })
            )),
            // Special characters
            KeyGroup("Symbols", listOf(
                KeyDef("|", icon = null, action = { onSendInput("|") }),
                KeyDef("&", icon = null, action = { onSendInput("&") }),
                KeyDef(";", icon = null, action = { onSendInput(";") }),
                KeyDef("/", icon = null, action = { onSendInput("/") }),
                KeyDef("\\", icon = null, action = { onSendInput("\\") }),
                KeyDef("~", icon = null, action = { onSendInput("~") }),
                KeyDef("`", icon = null, action = { onSendInput("`") }),
                KeyDef("$", icon = null, action = { onSendInput("$") }),
                KeyDef("{", icon = null, action = { onSendInput("{") }),
                KeyDef("}", icon = null, action = { onSendInput("}") }),
                KeyDef("[", icon = null, action = { onSendInput("[") }),
                KeyDef("]", icon = null, action = { onSendInput("]") })
            ))
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KeyboardBarBackground
    ) {
        Column {
            // Group selector row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                keyGroups.forEachIndexed { index, group ->
                    GroupTab(
                        name = group.name,
                        isSelected = currentGroup == index,
                        isZellij = group.name in listOf("Zellij", "Pane", "Tab"),
                        onClick = { currentGroup = index }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Ctrl modifier toggle
                ModifierKey(
                    label = "Ctrl",
                    isActive = ctrlActive,
                    activeColor = CtrlActiveColor,
                    onClick = { ctrlActive = !ctrlActive }
                )

                // Alt modifier toggle
                ModifierKey(
                    label = "Alt",
                    isActive = altActive,
                    activeColor = AltActiveColor,
                    onClick = { altActive = !altActive }
                )
            }

            HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)

            // Keys row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                keyGroups[currentGroup].keys.forEach { keyDef ->
                    KeyButton(
                        keyDef = keyDef,
                        ctrlActive = ctrlActive,
                        altActive = altActive,
                        isZellijGroup = keyGroups[currentGroup].name in listOf("Zellij", "Pane", "Tab"),
                        onClick = {
                            if (ctrlActive && keyDef.label.length == 1 && !keyDef.label.startsWith("^")) {
                                onCtrlKey(keyDef.label[0])
                                ctrlActive = false
                            } else if (altActive && keyDef.label.length == 1) {
                                onSendInput("\u001b${keyDef.label}")
                                altActive = false
                            } else {
                                keyDef.action()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupTab(
    name: String,
    isSelected: Boolean,
    isZellij: Boolean = false,
    onClick: () -> Unit
) {
    val selectedColor = if (isZellij) ZellijColor else KeyAccentColor

    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(2.dp),
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) selectedColor.copy(alpha = 0.3f) else Color.Transparent
    ) {
        Text(
            text = name,
            color = if (isSelected) selectedColor else KeyTextColor.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ModifierKey(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(28.dp),
        shape = RoundedCornerShape(4.dp),
        color = if (isActive) activeColor else KeyActiveBackground
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = label,
                color = if (isActive) Color.White else KeyTextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun KeyButton(
    keyDef: KeyDef,
    ctrlActive: Boolean,
    altActive: Boolean,
    isZellijGroup: Boolean = false,
    onClick: () -> Unit
) {
    val accentColor = if (isZellijGroup) ZellijColor else KeyAccentColor

    val bgColor = when {
        keyDef.accent -> accentColor.copy(alpha = 0.2f)
        ctrlActive || altActive -> CtrlActiveColor.copy(alpha = 0.1f)
        else -> KeyActiveBackground
    }

    val textColor = when {
        keyDef.accent -> accentColor
        else -> KeyTextColor
    }

    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(36.dp),
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            if (keyDef.icon != null) {
                Icon(
                    imageVector = keyDef.icon,
                    contentDescription = keyDef.label,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = keyDef.label,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private data class KeyGroup(
    val name: String,
    val keys: List<KeyDef>
)

private data class KeyDef(
    val label: String,
    val icon: ImageVector?,
    val action: () -> Unit,
    val accent: Boolean = false,
    val tooltip: String = ""
)

// Key codes (same as TerminalViewModel)
private const val KEY_ESCAPE = 1
private const val KEY_TAB = 2
private const val KEY_UP = 3
private const val KEY_DOWN = 4
private const val KEY_LEFT = 5
private const val KEY_RIGHT = 6
