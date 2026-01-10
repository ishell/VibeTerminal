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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Layout constants
private val EDGE_PADDING = 16.dp
private val BOTTOM_OFFSET = 120.dp
private val FAB_SIZE = 48.dp
private val ARROW_BUTTON_SIZE = 36.dp
private val FUNCTION_BUTTON_SIZE = 40.dp
private val INNER_RADIUS = 50.dp  // Arrow keys radius
private val OUTER_RADIUS = 95.dp  // Function keys radius
private const val BASE_ALPHA = 0.75f

// Colors
private val FAB_COLOR = Color(0xFF2196F3)
private val FAB_EXPANDED_COLOR = Color(0xFFE53935)
private val ARROW_COLOR = Color(0xFF455A64)
private val FUNCTION_COLOR = Color(0xFF37474F)
private val CTRL_COLOR = Color(0xFF795548)
private val CTRL_ACTIVE_COLOR = Color(0xFF4CAF50)

/**
 * Path-style floating action keyboard
 *
 * Design principles:
 * - Two-ring layout: inner ring for arrows, outer ring for function keys
 * - Fixed angular positions to prevent overlap
 * - Visual grouping of related functions
 * - Most common terminal keys: Esc, Tab, Arrows, Ctrl+C/D/Z
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
    val innerRadiusPx = with(density) { INNER_RADIUS.toPx() }
    val outerRadiusPx = with(density) { OUTER_RADIUS.toPx() }

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

    // Animation scale
    val expandScale by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expand"
    )

    // Center position of FAB (for calculating button positions)
    val centerX = animatedX + fabSizePx / 2
    val centerY = offsetY + fabSizePx / 2

    // Direction multiplier based on side
    val dirMult = if (isOnLeftSide) 1f else -1f

    Box(modifier = modifier.alpha(BASE_ALPHA)) {
        if (expandScale > 0.01f) {
            // ===== Inner Ring: Arrow Keys (D-pad style) =====
            // Positioned in a cross pattern

            // Up arrow - top
            ArrowButton(
                icon = Icons.Default.KeyboardArrowUp,
                label = "↑",
                centerX = centerX,
                centerY = centerY,
                angle = -PI.toFloat() / 2,  // -90° (up)
                radius = innerRadiusPx,
                scale = expandScale,
                onClick = { onKey(KEY_UP) }
            )

            // Down arrow - bottom
            ArrowButton(
                icon = Icons.Default.KeyboardArrowDown,
                label = "↓",
                centerX = centerX,
                centerY = centerY,
                angle = PI.toFloat() / 2,  // 90° (down)
                radius = innerRadiusPx,
                scale = expandScale,
                onClick = { onKey(KEY_DOWN) }
            )

            // Left arrow
            ArrowButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                label = "←",
                centerX = centerX,
                centerY = centerY,
                angle = if (isOnLeftSide) PI.toFloat() * 0.85f else PI.toFloat() * 0.15f,
                radius = innerRadiusPx,
                scale = expandScale,
                onClick = { onKey(KEY_LEFT) }
            )

            // Right arrow
            ArrowButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                label = "→",
                centerX = centerX,
                centerY = centerY,
                angle = if (isOnLeftSide) PI.toFloat() * 0.15f else PI.toFloat() * 0.85f,
                radius = innerRadiusPx,
                scale = expandScale,
                onClick = { onKey(KEY_RIGHT) }
            )

            // ===== Outer Ring: Function Keys =====
            // Arranged in an arc, avoiding overlap with arrows

            val outerKeys = if (isOnLeftSide) {
                listOf(
                    FunctionKey("Esc", -55f, FUNCTION_COLOR) { onKey(KEY_ESCAPE) },
                    FunctionKey("Tab", -20f, FUNCTION_COLOR) { onKey(KEY_TAB) },
                    FunctionKey("^C", 15f, CTRL_COLOR) { onCtrlKey('C') },
                    FunctionKey("^D", 50f, CTRL_COLOR) { onCtrlKey('D') },
                    FunctionKey("^Z", 85f, CTRL_COLOR) { onCtrlKey('Z') }
                )
            } else {
                listOf(
                    FunctionKey("Esc", 180f + 55f, FUNCTION_COLOR) { onKey(KEY_ESCAPE) },
                    FunctionKey("Tab", 180f + 20f, FUNCTION_COLOR) { onKey(KEY_TAB) },
                    FunctionKey("^C", 180f - 15f, CTRL_COLOR) { onCtrlKey('C') },
                    FunctionKey("^D", 180f - 50f, CTRL_COLOR) { onCtrlKey('D') },
                    FunctionKey("^Z", 180f - 85f, CTRL_COLOR) { onCtrlKey('Z') }
                )
            }

            outerKeys.forEach { key ->
                FunctionButton(
                    label = key.label,
                    centerX = centerX,
                    centerY = centerY,
                    angleDegrees = key.angleDegrees,
                    radius = outerRadiusPx,
                    scale = expandScale,
                    color = key.color,
                    onClick = {
                        key.action()
                        isExpanded = false
                    }
                )
            }
        }

        // ===== Main FAB =====
        Surface(
            modifier = Modifier
                .offset { IntOffset(animatedX.roundToInt(), offsetY.roundToInt()) }
                .size(FAB_SIZE)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            // Snap to nearest side
                            val fabCenterX = offsetX + fabSizePx / 2
                            isOnLeftSide = fabCenterX < screenWidth / 2
                            offsetX = if (isOnLeftSide) {
                                edgePaddingPx
                            } else {
                                screenWidth - fabSizePx - edgePaddingPx
                            }
                        },
                        onDragCancel = { }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidth - fabSizePx)
                        offsetY = (offsetY + dragAmount.y).coerceIn(fabSizePx, screenHeight - fabSizePx - 50)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        isExpanded = !isExpanded
                    }
                },
            shape = CircleShape,
            color = if (isExpanded) FAB_EXPANDED_COLOR.copy(alpha = 0.9f) else FAB_COLOR.copy(alpha = 0.85f),
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Terminal,
                    contentDescription = if (isExpanded) "Close" else "Open keyboard",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Arrow key button - circular, used for direction keys
 */
@Composable
private fun ArrowButton(
    icon: ImageVector,
    label: String,
    centerX: Float,
    centerY: Float,
    angle: Float,
    radius: Float,
    scale: Float,
    onClick: () -> Unit
) {
    val btnX = centerX + cos(angle) * radius - with(LocalDensity.current) { ARROW_BUTTON_SIZE.toPx() } / 2
    val btnY = centerY + sin(angle) * radius - with(LocalDensity.current) { ARROW_BUTTON_SIZE.toPx() } / 2

    Surface(
        modifier = Modifier
            .offset { IntOffset(btnX.roundToInt(), btnY.roundToInt()) }
            .size(ARROW_BUTTON_SIZE)
            .alpha(scale)
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            },
        shape = CircleShape,
        color = ARROW_COLOR.copy(alpha = 0.9f),
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Function key button - rounded rectangle, used for Esc, Tab, Ctrl combos
 */
@Composable
private fun FunctionButton(
    label: String,
    centerX: Float,
    centerY: Float,
    angleDegrees: Float,
    radius: Float,
    scale: Float,
    color: Color,
    onClick: () -> Unit
) {
    val angleRad = angleDegrees * PI.toFloat() / 180f
    val btnSizePx = with(LocalDensity.current) { FUNCTION_BUTTON_SIZE.toPx() }
    val btnX = centerX + cos(angleRad) * radius - btnSizePx / 2
    val btnY = centerY + sin(angleRad) * radius - btnSizePx / 2

    Surface(
        modifier = Modifier
            .offset { IntOffset(btnX.roundToInt(), btnY.roundToInt()) }
            .size(FUNCTION_BUTTON_SIZE)
            .alpha(scale)
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            },
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.9f),
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color.White,
                fontSize = if (label.length > 2) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class FunctionKey(
    val label: String,
    val angleDegrees: Float,
    val color: Color,
    val action: () -> Unit
)

// Key codes (same as TerminalViewModel)
private const val KEY_ESCAPE = 1
private const val KEY_TAB = 2
private const val KEY_UP = 3
private const val KEY_DOWN = 4
private const val KEY_LEFT = 5
private const val KEY_RIGHT = 6
