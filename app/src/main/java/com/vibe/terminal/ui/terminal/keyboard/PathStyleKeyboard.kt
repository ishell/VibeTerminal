package com.vibe.terminal.ui.terminal.keyboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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

// Edge padding from screen border
private val EDGE_PADDING = 16.dp
// Bottom offset to avoid overlapping with horizontal keyboard
private val BOTTOM_OFFSET = 120.dp
// FAB size (smaller than before)
private val FAB_SIZE = 44.dp
// Shortcut button size
private val BUTTON_SIZE = 38.dp
// Overall transparency
private const val BASE_ALPHA = 0.7f

/**
 * Path-style floating action keyboard
 * Shows a draggable FAB that expands into a radial menu of shortcuts
 * Snaps to left or right side of screen
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
    val edgePaddingPx = with(density) { EDGE_PADDING.toPx() }
    val bottomOffsetPx = with(density) { BOTTOM_OFFSET.toPx() }
    val fabSizePx = with(density) { FAB_SIZE.toPx() }

    // Track which side (left = true, right = false)
    var isOnLeftSide by remember { mutableStateOf(true) }

    // FAB position - default: left side, bottom with offset
    var offsetX by remember { mutableFloatStateOf(edgePaddingPx) }
    var offsetY by remember { mutableFloatStateOf(screenHeight - bottomOffsetPx - fabSizePx) }

    // Animated position for smooth snapping
    val animatedX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "posX"
    )

    // Expanded state
    var isExpanded by remember { mutableStateOf(false) }

    // Ctrl mode (for Ctrl+key combinations)
    var ctrlMode by remember { mutableStateOf(false) }

    // Dragging state
    var isDragging by remember { mutableStateOf(false) }

    // Animation
    val expandScale by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expand"
    )

    // Shortcut keys configuration - adjust directions based on side
    val shortcuts = remember(isOnLeftSide) {
        if (isOnLeftSide) {
            // Left side: expand to the right
            listOf(
                ShortcutKey("Esc", null, 1.0, -1.0) { onKey(KEY_ESCAPE) },
                ShortcutKey("Tab", null, 1.0, 0.0) { onKey(KEY_TAB) },
                ShortcutKey("↑", Icons.Default.KeyboardArrowUp, 0.7, -0.7) { onKey(KEY_UP) },
                ShortcutKey("→", Icons.AutoMirrored.Filled.KeyboardArrowRight, 1.0, 0.5) { onKey(KEY_RIGHT) },
                ShortcutKey("↓", Icons.Default.KeyboardArrowDown, 0.7, 0.7) { onKey(KEY_DOWN) },
                ShortcutKey("←", Icons.AutoMirrored.Filled.KeyboardArrowLeft, 0.3, 0.3) { onKey(KEY_LEFT) },
                ShortcutKey("^C", null, 0.5, -0.5) { onCtrlKey('C') },
                ShortcutKey("^D", null, 0.3, -0.3) { onCtrlKey('D') }
            )
        } else {
            // Right side: expand to the left
            listOf(
                ShortcutKey("Esc", null, -1.0, -1.0) { onKey(KEY_ESCAPE) },
                ShortcutKey("Tab", null, -1.0, 0.0) { onKey(KEY_TAB) },
                ShortcutKey("↑", Icons.Default.KeyboardArrowUp, -0.7, -0.7) { onKey(KEY_UP) },
                ShortcutKey("→", Icons.AutoMirrored.Filled.KeyboardArrowRight, -0.3, 0.3) { onKey(KEY_RIGHT) },
                ShortcutKey("↓", Icons.Default.KeyboardArrowDown, -0.7, 0.7) { onKey(KEY_DOWN) },
                ShortcutKey("←", Icons.AutoMirrored.Filled.KeyboardArrowLeft, -1.0, 0.5) { onKey(KEY_LEFT) },
                ShortcutKey("^C", null, -0.5, -0.5) { onCtrlKey('C') },
                ShortcutKey("^D", null, -0.3, -0.3) { onCtrlKey('D') }
            )
        }
    }

    Box(modifier = modifier.alpha(BASE_ALPHA)) {
        // Expanded shortcut buttons
        if (expandScale > 0.01f) {
            shortcuts.forEach { shortcut ->
                val radius = 65.dp
                val radiusPx = with(density) { radius.toPx() }

                // Calculate position based on direction
                val angle = kotlin.math.atan2(shortcut.dirY, shortcut.dirX)
                val distance = radiusPx * expandScale
                val btnX = animatedX + cos(angle).toFloat() * distance
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
            val ctrlBtnX = if (isOnLeftSide) animatedX + 70 else animatedX - 70
            Surface(
                modifier = Modifier
                    .offset { IntOffset(ctrlBtnX.roundToInt(), (offsetY - 50).roundToInt()) }
                    .size(36.dp)
                    .scale(expandScale)
                    .alpha(expandScale)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            ctrlMode = !ctrlMode
                        }
                    },
                shape = CircleShape,
                color = if (ctrlMode) Color(0xFF4CAF50) else Color(0xFF424242).copy(alpha = 0.9f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Ctrl",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Main FAB
        Surface(
            modifier = Modifier
                .offset { IntOffset(animatedX.roundToInt(), offsetY.roundToInt()) }
                .size(FAB_SIZE)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            // Snap to nearest side
                            val centerX = offsetX + fabSizePx / 2
                            isOnLeftSide = centerX < screenWidth / 2
                            offsetX = if (isOnLeftSide) {
                                edgePaddingPx
                            } else {
                                screenWidth - fabSizePx - edgePaddingPx
                            }
                        },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidth - fabSizePx)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeight - fabSizePx - 50)
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
            color = if (isExpanded) Color(0xFFE53935).copy(alpha = 0.9f) else Color(0xFF2196F3).copy(alpha = 0.85f),
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Terminal,
                    contentDescription = if (isExpanded) "Close" else "Open keyboard",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
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
            .size(BUTTON_SIZE)
            .scale(scale)
            .alpha(scale)
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            },
        shape = CircleShape,
        color = if (shortcut.label.startsWith("^")) {
            if (isCtrlMode) Color(0xFF4CAF50).copy(alpha = 0.9f) else Color(0xFF795548).copy(alpha = 0.85f)
        } else {
            Color(0xFF424242).copy(alpha = 0.85f)
        },
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (shortcut.icon != null) {
                Icon(
                    imageVector = shortcut.icon,
                    contentDescription = shortcut.label,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = shortcut.label,
                    color = Color.White,
                    fontSize = 10.sp,
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
