package com.vibe.terminal.ui.terminal.keyboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Path-style floating action keyboard
 * Shows a draggable FAB that expands into a radial menu of shortcuts
 */
@Composable
fun PathStyleKeyboard(
    onKey: (Int) -> Unit,
    onCtrlKey: (Char) -> Unit,
    onSendInput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // FAB position (default bottom-right)
    var offsetX by remember { mutableFloatStateOf(screenWidth - 200f) }
    var offsetY by remember { mutableFloatStateOf(screenHeight - 300f) }

    // Expanded state
    var isExpanded by remember { mutableStateOf(false) }

    // Ctrl mode (for Ctrl+key combinations)
    var ctrlMode by remember { mutableStateOf(false) }

    // Animation
    val expandScale by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expand"
    )

    val fabRotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rotation"
    )

    // Shortcut keys configuration
    val shortcuts = remember {
        listOf(
            ShortcutKey("Esc", null, 0.0, -1.0) { onKey(KEY_ESCAPE) },
            ShortcutKey("Tab", null, 1.0, -1.0) { onKey(KEY_TAB) },
            ShortcutKey("↑", Icons.Default.KeyboardArrowUp, 0.5, -0.5) { onKey(KEY_UP) },
            ShortcutKey("→", Icons.AutoMirrored.Filled.KeyboardArrowRight, 1.0, 0.0) { onKey(KEY_RIGHT) },
            ShortcutKey("↓", Icons.Default.KeyboardArrowDown, 0.5, 0.5) { onKey(KEY_DOWN) },
            ShortcutKey("←", Icons.AutoMirrored.Filled.KeyboardArrowLeft, 0.0, 0.0) { onKey(KEY_LEFT) },
            ShortcutKey("^C", null, -0.5, -0.5) { onCtrlKey('C') },
            ShortcutKey("^D", null, -0.5, 0.5) { onCtrlKey('D') }
        )
    }

    Box(modifier = modifier) {
        // Expanded shortcut buttons
        if (expandScale > 0.01f) {
            shortcuts.forEach { shortcut ->
                val radius = 80.dp
                val radiusPx = with(density) { radius.toPx() }

                // Calculate position based on direction
                val angle = kotlin.math.atan2(shortcut.dirY, shortcut.dirX)
                val distance = radiusPx * expandScale
                val btnX = offsetX + cos(angle).toFloat() * distance
                val btnY = offsetY + sin(angle).toFloat() * distance

                ShortcutButton(
                    shortcut = shortcut,
                    isCtrlMode = ctrlMode,
                    scale = expandScale,
                    modifier = Modifier.offset { IntOffset(btnX.roundToInt(), btnY.roundToInt()) },
                    onClick = {
                        shortcut.action()
                        if (!ctrlMode) {
                            isExpanded = false
                        }
                    }
                )
            }

            // Ctrl toggle button
            Surface(
                modifier = Modifier
                    .offset { IntOffset((offsetX - 70).roundToInt(), (offsetY).roundToInt()) }
                    .size(44.dp)
                    .scale(expandScale)
                    .alpha(expandScale)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            ctrlMode = !ctrlMode
                        }
                    },
                shape = CircleShape,
                color = if (ctrlMode) Color(0xFF4CAF50) else Color(0xFF424242)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Ctrl",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Main FAB
        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(56.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidth - 56.dp.toPx())
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeight - 56.dp.toPx())
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        isExpanded = !isExpanded
                        if (!isExpanded) {
                            ctrlMode = false
                        }
                    }
                },
            shape = CircleShape,
            color = if (isExpanded) Color(0xFFE53935) else Color(0xFF2196F3),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Terminal,
                    contentDescription = if (isExpanded) "Close" else "Open keyboard",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ShortcutButton(
    shortcut: ShortcutKey,
    isCtrlMode: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .alpha(scale)
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            },
        shape = CircleShape,
        color = if (shortcut.label.startsWith("^")) {
            if (isCtrlMode) Color(0xFF4CAF50) else Color(0xFF795548)
        } else {
            Color(0xFF424242)
        },
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (shortcut.icon != null) {
                Icon(
                    imageVector = shortcut.icon,
                    contentDescription = shortcut.label,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = shortcut.label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class ShortcutKey(
    val label: String,
    val icon: ImageVector?,
    val dirX: Double,  // Direction X (-1 to 1)
    val dirY: Double,  // Direction Y (-1 to 1)
    val action: () -> Unit
)

// Key codes (same as TerminalViewModel)
private const val KEY_ESCAPE = 1
private const val KEY_TAB = 2
private const val KEY_UP = 3
private const val KEY_DOWN = 4
private const val KEY_LEFT = 5
private const val KEY_RIGHT = 6
