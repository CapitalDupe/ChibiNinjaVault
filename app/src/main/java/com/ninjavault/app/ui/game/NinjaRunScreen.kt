package com.ninjavault.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ninjavault.app.vault.ActivationSequenceManager
import com.ninjavault.app.vault.SwipeDirection
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private const val LANE_COUNT = 3
private const val NINJA_Y_FRACTION = 0.82f
private const val HIT_ZONE_START = NINJA_Y_FRACTION - 0.04f
private const val HIT_ZONE_END = NINJA_Y_FRACTION + 0.04f
private const val COIN_POINTS = 50
private const val HIT_PENALTY = 200

data class RunnerObstacle(val id: Long, val lane: Int, val progress: Float, val isCoin: Boolean)

/**
 * The main "game" screen: a lightweight 3-lane endless runner. Swipe left/right
 * to change lanes, swipe up to jump (clears obstacles), swipe down to slide.
 * Collect coins for bonus points; running into an obstacle costs points.
 *
 * Every swipe is also fed into [activationManager]; when the player's swipes
 * match their configured unlock pattern, [onUnlocked] fires and the app
 * transitions toward the vault. This is a deliberate, user-configured combination
 * — nothing here is hidden from the player or disguises what the app does.
 */
@Composable
fun NinjaRunScreen(
    activationManager: ActivationSequenceManager,
    hapticsEnabled: Boolean,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lane by remember { mutableStateOf(1) } // 0 = left, 1 = center, 2 = right
    var isJumping by remember { mutableStateOf(false) }
    var isSliding by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var obstacles by remember { mutableStateOf(listOf<RunnerObstacle>()) }
    var nextId by remember { mutableStateOf(0L) }
    var flashMessage by remember { mutableStateOf<String?>(null) }

    val haptic = LocalHapticFeedback.current
    val activationState by activationManager.state.collectAsState()

    LaunchedEffect(activationState) {
        when (val s = activationState) {
            is ActivationSequenceManager.ActivationState.Unlocked -> onUnlocked()
            is ActivationSequenceManager.ActivationState.Failed -> {
                flashMessage = "..."
                delay(400)
                flashMessage = null
            }
            is ActivationSequenceManager.ActivationState.LockedOut -> {
                flashMessage = "Too many attempts — slow down"
                // Clear the message once the lockout has actually ended.
                delay(s.remainingMillis.coerceAtLeast(0L))
                flashMessage = null
            }
            else -> {}
        }
    }

    // Game loop: spawn obstacles/coins.
    LaunchedEffect(Unit) {
        while (true) {
            delay(900)
            obstacles = obstacles + RunnerObstacle(
                id = nextId++,
                lane = Random.nextInt(LANE_COUNT),
                progress = 0f,
                isCoin = Random.nextBoolean()
            )
        }
    }

    // Game loop: advance, collect coins, resolve collisions.
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            var delta = 1 // distance score
            val next = ArrayList<RunnerObstacle>(obstacles.size)
            for (o in obstacles) {
                val moved = o.copy(progress = o.progress + 0.02f)
                val inHitZone = moved.progress in HIT_ZONE_START..HIT_ZONE_END && moved.lane == lane
                when {
                    inHitZone && moved.isCoin -> delta += COIN_POINTS
                    inHitZone && !moved.isCoin && !isJumping -> delta -= HIT_PENALTY
                    moved.progress < 1.2f -> next.add(moved)
                }
            }
            obstacles = next
            score = (score + delta).coerceAtLeast(0)
        }
    }

    fun handleSwipe(direction: SwipeDirection) {
        when (direction) {
            SwipeDirection.LEFT -> lane = (lane - 1).coerceAtLeast(0)
            SwipeDirection.RIGHT -> lane = (lane + 1).coerceAtMost(LANE_COUNT - 1)
            SwipeDirection.UP -> isJumping = true
            SwipeDirection.DOWN -> isSliding = true
        }
        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        activationManager.onSwipe(direction)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1B1436))
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        if (abs(dx) > abs(dy)) {
                            if (abs(dx) > 60) handleSwipe(if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT)
                        } else {
                            if (abs(dy) > 60) handleSwipe(if (dy > 0) SwipeDirection.DOWN else SwipeDirection.UP)
                        }
                    }
                ) { change, amount ->
                    change.consume()
                    dx += amount.x
                    dy += amount.y
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawTrack(this)
            drawObstacles(this, obstacles)
            drawNinja(this, lane, isJumping, isSliding)
        }

        // Reset transient pose states shortly after triggering.
        LaunchedEffect(isJumping) { if (isJumping) { delay(350); isJumping = false } }
        LaunchedEffect(isSliding) { if (isSliding) { delay(350); isSliding = false } }

        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            GameHud(score = score, flashMessage = flashMessage)
        }
    }
}

private fun drawTrack(scope: DrawScope) {
    val laneWidth = scope.size.width / LANE_COUNT
    for (i in 1 until LANE_COUNT) {
        scope.drawLine(
            color = Color(0x33FFFFFF),
            start = Offset(laneWidth * i, 0f),
            end = Offset(laneWidth * i, scope.size.height),
            strokeWidth = 4f
        )
    }
}

private fun drawObstacles(scope: DrawScope, obstacles: List<RunnerObstacle>) {
    val laneWidth = scope.size.width / LANE_COUNT
    obstacles.forEach { obstacle ->
        val cx = laneWidth * obstacle.lane + laneWidth / 2
        val cy = scope.size.height * obstacle.progress
        scope.drawCircle(
            color = if (obstacle.isCoin) Color(0xFFFFD54F) else Color(0xFFEF5350),
            radius = if (obstacle.isCoin) 18f else 26f,
            center = Offset(cx, cy)
        )
    }
}

private fun drawNinja(scope: DrawScope, lane: Int, isJumping: Boolean, isSliding: Boolean) {
    val laneWidth = scope.size.width / LANE_COUNT
    val cx = laneWidth * lane + laneWidth / 2
    val baseY = scope.size.height * NINJA_Y_FRACTION
    val cy = if (isJumping) baseY - 90f else baseY
    val height = if (isSliding) 30f else 60f
    scope.drawCircle(color = Color(0xFF7C4DFF), radius = 22f, center = Offset(cx, cy - height))
    scope.drawRoundRect(
        color = Color(0xFF512DA8),
        topLeft = Offset(cx - 18f, cy - height + 18f),
        size = androidx.compose.ui.geometry.Size(36f, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
    )
}

@Composable
private fun GameHud(score: Int, flashMessage: String?) {
    androidx.compose.material3.Text(
        text = "Score: $score" + (flashMessage?.let { "  ·  $it" } ?: ""),
        color = Color.White,
        modifier = Modifier.background(Color(0x66000000))
    )
}
