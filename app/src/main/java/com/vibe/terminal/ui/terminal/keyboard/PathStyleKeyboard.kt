package com.vibe.terminal.ui.terminal.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Layout constants
private val EDGE_PADDING = 16.dp
private val BOTTOM_OFFSET = 120.dp
private val FAB_SIZE = 48.dp
private val BUTTON_SIZE = 42.dp
private val MENU_RADIUS = 85.dp
private const val BASE_ALPHA = 0.8f

// Animation timing (Path style)
private const val STAGGER_DELAY_MS = 40L  // Delay between each button
private const val ANIMATION_DURATION_MS = 300

// Colors
private val FAB_COLOR = Color(0xFF2196F3)
private val FAB_EXPANDED_COLOR = Color(0xFFE53935)
private val BUTTON_COLOR = Color(0xFF37474F)
private val CTRL_COLOR = Color(0xFF5D4037)

/**
 * Path-style floating action keyboard
 *
 * Implements the classic Path app menu interaction:
 * - Staggered animation: buttons pop out sequentially with 40ms delay
 * - Spring bounce effect for each button
 * - 90-degree arc expansion towards screen center
 * - All buttons on one side only
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
    val menuRadiusPx = with(density) { MENU_RADIUS.toPx() }

    // Track which side (left = true, right = false)
    var isOnLeftSide by remember { mutableStateOf(true) }

    // FAB position
    var offsetX by remember { mutableFloatStateOf(edgePaddingPx) }
    var offsetY by remember { mutableFloatStateOf(screenHeight - bottomOffsetPx - fabSizePx) }

    // Animated X position for smooth snapping
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

    // Individual button animations (Path-style staggered)
    val buttonAnimations = remember { List(7) { Animatable(0f) } }

    // Trigger staggered animation when expanded state changes
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            // Staggered expand: each button animates with delay
            buttonAnimations.forEachIndexed { index, animatable ->
                launch {
                    delay(index * STAGGER_DELAY_MS)
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.5f,  // Bouncy
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
        } else {
            // Collapse: reverse order, faster
            buttonAnimations.reversed().forEachIndexed { index, animatable ->
                launch {
                    delay(index * (STAGGER_DELAY_MS / 2))
                    animatable.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = Spring.StiffnessHigh
                        )
                    )
                }
            }
        }
    }

    // Center position of FAB
    val centerX = animatedX + fabSizePx / 2
    val centerY = offsetY + fabSizePx / 2

    // Base angle: left side expands right (0°), right side expands left (180°)
    val baseAngle = if (isOnLeftSide) 0f else 180f

    // Menu items: arranged in 90° arc (-45° to +45° from base)
    // Order matters for stagger animation
    data class MenuItem(
        val label: String,
        val icon: ImageVector?,
        val angleOffset: Float,  // Offset from base angle in degrees
        val color: Color,
        val action: () -> Unit
    )

    val menuItems = listOf(
        MenuItem("Esc", null, -45f, BUTTON_COLOR) { onKey(KEY_ESCAPE) },
        MenuItem("↑", Icons.Default.KeyboardArrowUp, -27f, BUTTON_COLOR) { onKey(KEY_UP) },
        MenuItem("Tab", null, -9f, BUTTON_COLOR) { onKey(KEY_TAB) },
        MenuItem("^C", null, 9f, CTRL_COLOR) { onCtrlKey('C') },
        MenuItem("↓", Icons.Default.KeyboardArrowDown, 27f, BUTTON_COLOR) { onKey(KEY_DOWN) },
        MenuItem("^D", null, 45f, CTRL_COLOR) { onCtrlKey('D') },
        MenuItem("^Z", null, 63f, CTRL_COLOR) { onCtrlKey('Z') }
    )

    Box(modifier = modifier.alpha(BASE_ALPHA)) {
        // Menu buttons with staggered animation
        menuItems.forEachIndexed { index, item ->
            val animProgress = buttonAnimations[index].value

            if (animProgress > 0.01f) {
                val angleDegrees = baseAngle + item.angleOffset
                val angleRad = angleDegrees * PI.toFloat() / 180f
                val currentRadius = menuRadiusPx * animProgress
                val btnSizePx = with(density) { BUTTON_SIZE.toPx() }

                val btnX = centerX + cos(angleRad) * currentRadius - btnSizePx / 2
                val btnY = centerY + sin(angleRad) * currentRadius - btnSizePx / 2

                Surface(
                    modifier = Modifier
                        .offset { IntOffset(btnX.roundToInt(), btnY.roundToInt()) }
                        .size(BUTTON_SIZE)
                        .scale(animProgress)
                        .alpha(animProgress)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                item.action()
                                isExpanded = false
                            }
                        },
                    shape = CircleShape,
                    color = item.color.copy(alpha = 0.92f),
                    shadowElevation = (4 * animProgress).dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (item.icon != null) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(
                                text = item.label,
                                color = Color.White,
                                fontSize = if (item.label.length > 2) 11.sp else 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
            color = if (isExpanded) FAB_EXPANDED_COLOR.copy(alpha = 0.95f) else FAB_COLOR.copy(alpha = 0.9f),
            shadowElevation = 6.dp
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

// Key codes (must match TerminalViewModel)
private const val KEY_TAB = 3
private const val KEY_ESCAPE = 4
private const val KEY_UP = 5
private const val KEY_DOWN = 6
private const val KEY_RIGHT = 7
private const val KEY_LEFT = 8
