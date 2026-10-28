package com.ninjavault.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ninjavault.app.vault.ActivationSequenceManager
import com.ninjavault.app.vault.SwipeDirection
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

private const val NINJA_Y = 0.80f
private const val COIN_POINTS = 50

private enum class ItemType { COIN, ROCK, GATE, BRANCH }
private data class RunnerItem(
    val id: Long,
    val lane: Int,
    val depth: Float,
    val type: ItemType,
    val resolved: Boolean = false
)

/** Compose pseudo-3D endless runner. Swipes continue to feed the configured vault activation sequence. */
@Composable
fun NinjaRunScreen(
    activationManager: ActivationSequenceManager,
    hapticsEnabled: Boolean,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lane by remember { mutableIntStateOf(1) }
    var jumping by remember { mutableStateOf(false) }
    var sliding by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var coins by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var distance by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf(listOf<RunnerItem>()) }
    var nextId by remember { mutableLongStateOf(0L) }
    var gameOver by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("SWIPE TO DODGE  •  COLLECT COINS") }
    var invulnerable by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val activationState by activationManager.state.collectAsState()

    LaunchedEffect(activationState) {
        when (val state = activationState) {
            is ActivationSequenceManager.ActivationState.Unlocked -> onUnlocked()
            is ActivationSequenceManager.ActivationState.Failed -> {
                message = "KEEP RUNNING"
                delay(650)
                message = if (gameOver) "RUN OVER  •  SWIPE TO RETRY" else "SWIPE TO DODGE  •  COLLECT COINS"
            }
            is ActivationSequenceManager.ActivationState.LockedOut -> {
                message = "TOO MANY ATTEMPTS — SLOW DOWN"
                delay(state.remainingMillis.coerceAtLeast(0L))
                message = if (gameOver) "RUN OVER  •  SWIPE TO RETRY" else "SWIPE TO DODGE  •  COLLECT COINS"
            }
            else -> Unit
        }
    }

    LaunchedEffect(gameOver) {
        if (gameOver) return@LaunchedEffect
        while (!gameOver) {
            delay((520L - distance / 8L).coerceAtLeast(230L))
            if (!gameOver) {
                val type = when (Random.nextInt(100)) {
                    in 0..39 -> ItemType.COIN
                    in 40..64 -> ItemType.ROCK
                    in 65..82 -> ItemType.GATE
                    else -> ItemType.BRANCH
                }
                items = items + RunnerItem(nextId++, Random.nextInt(3), 0f, type)
            }
        }
    }

    LaunchedEffect(gameOver) {
        if (gameOver) return@LaunchedEffect
        while (!gameOver) {
            delay(16)
            val speed = (0.0038f + distance / 180000f).coerceAtMost(0.009f)
            var gained = 1
            val next = ArrayList<RunnerItem>(items.size)
            for (old in items) {
                var item = old.copy(depth = old.depth + speed)
                val hit = item.depth in 0.78f..0.90f && item.lane == lane && !item.resolved
                if (hit) {
                    item = item.copy(resolved = true)
                    when (item.type) {
                        ItemType.COIN -> {
                            coins++
                            gained += COIN_POINTS
                            message = "+50  •  NICE CATCH!"
                        }
                        ItemType.ROCK -> if (!jumping && invulnerable == 0) {
                            score = (score - 100).coerceAtLeast(0)
                            lives--
                            invulnerable = 35
                            message = "ROCK HIT  •  JUMP NEXT TIME"
                        }
                        ItemType.GATE -> if (!sliding && invulnerable == 0) {
                            score = (score - 100).coerceAtLeast(0)
                            lives--
                            invulnerable = 35
                            message = "GATE HIT  •  SLIDE NEXT TIME"
                        }
                        ItemType.BRANCH -> if (!jumping && invulnerable == 0) {
                            score = (score - 100).coerceAtLeast(0)
                            lives--
                            invulnerable = 35
                            message = "BRANCH HIT  •  JUMP NEXT TIME"
                        }
                    }
                    if (lives <= 0) {
                        lives = 0
                        gameOver = true
                        message = "RUN OVER  •  SWIPE TO RETRY"
                    }
                }
                if (item.depth < 1.08f) next.add(item)
            }
            items = next
            distance++
            score += gained
            if (invulnerable > 0) invulnerable--
            if (distance > 0 && distance % 300 == 0) message = "SPEED UP!"
        }
    }

    LaunchedEffect(jumping) { if (jumping) { delay(520); jumping = false } }
    LaunchedEffect(sliding) { if (sliding) { delay(500); sliding = false } }

    fun handleSwipe(direction: SwipeDirection) {
        if (gameOver) {
            gameOver = false
            lane = 1
            jumping = false
            sliding = false
            score = 0
            coins = 0
            lives = 3
            distance = 0
            items = emptyList()
            invulnerable = 0
            message = "GO!  •  SWIPE TO DODGE"
        } else {
            when (direction) {
                SwipeDirection.LEFT -> lane = (lane - 1).coerceAtLeast(0)
                SwipeDirection.RIGHT -> lane = (lane + 1).coerceAtMost(2)
                SwipeDirection.UP -> { jumping = true; sliding = false }
                SwipeDirection.DOWN -> { sliding = true; jumping = false }
            }
        }
        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        activationManager.onSwipe(direction)
    }

    Box(
        modifier.fillMaxSize().background(Color(0xFF101B2B))
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        if (abs(dx) > abs(dy)) {
                            if (abs(dx) > 55) handleSwipe(if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT)
                        } else if (abs(dy) > 55) {
                            handleSwipe(if (dy > 0) SwipeDirection.DOWN else SwipeDirection.UP)
                        }
                    }
                ) { change, amount -> change.consume(); dx += amount.x; dy += amount.y }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawWorld(distance)
            drawItems(items)
            drawRunner(lane, jumping, sliding, invulnerable > 0)
            if (gameOver) drawRect(Color(0xAA06111D))
        }
        Box(Modifier.align(Alignment.TopCenter).padding(top = 28.dp)) {
            Text(
                "SCORE  $score     ◆  $coins     ♥  $lives",
                color = Color.White,
                fontSize = 19.sp,
                modifier = Modifier.background(Color(0xAA07111E), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp)) {
            Text(
                if (gameOver) "RUN OVER  •  SWIPE TO RETRY" else message,
                color = if (gameOver) Color(0xFFFFD166) else Color(0xFFE6F1FF),
                fontSize = 13.sp,
                modifier = Modifier.background(Color(0xAA07111E), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}

private fun DrawScope.drawWorld(distance: Int) {
    val w = size.width
    val h = size.height
    drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF132B3A), Color(0xFF3A5C52), Color(0xFF182B2A))))
    drawCircle(Color(0x44D9F0E8), w * 0.075f, Offset(w * 0.78f, h * 0.16f))
    val mountain = Path().apply {
        moveTo(0f, h * .39f); lineTo(w * .18f, h * .22f); lineTo(w * .33f, h * .38f)
        lineTo(w * .53f, h * .19f); lineTo(w * .75f, h * .39f); lineTo(w, h * .25f)
        lineTo(w, h * .58f); lineTo(0f, h * .58f); close()
    }
    drawPath(mountain, Color(0xFF203D43))
    val road = Path().apply {
        moveTo(w * .43f, h * .34f); lineTo(w * .57f, h * .34f)
        lineTo(w * 1.08f, h); lineTo(w * -.08f, h); close()
    }
    drawPath(road, Color(0xFF34423E))
    val edge = Color(0xFFB9A77A)
    drawLine(edge.copy(alpha = .65f), Offset(w * .43f, h * .34f), Offset(w * -.08f, h), 3f)
    drawLine(edge.copy(alpha = .65f), Offset(w * .57f, h * .34f), Offset(w * 1.08f, h), 3f)
    val phase = (distance % 80) / 80f
    for (laneLine in 1..2) {
        val xTop = w * (.43f + .07f * laneLine)
        val xBottom = w * (.333f * laneLine)
        for (i in 0..7) {
            val p = ((i / 8f + phase) % 1f)
            val y = h * (.36f + p * .62f)
            val t = (y / h - .34f) / .66f
            val x = xTop + (xBottom - xTop) * t
            drawLine(Color(0x77E5D9B5), Offset(x, y), Offset(x, y + 4f + 15f * t), 2f + 4f * t)
        }
    }
    for (i in 0..8) {
        val p = ((i / 9f + phase) % 1f)
        val y = h * (.30f + p * .72f)
        val scale = .18f + p * .9f
        val lx = w * (.14f - p * .20f) + (i % 3) * 15f
        val rx = w * (.86f + p * .20f) - (i % 3) * 15f
        drawLine(Color(0xFF152F2C), Offset(lx, y - 70f * scale), Offset(lx - 5f * scale, y + 50f * scale), 5f * scale)
        drawLine(Color(0xFF152F2C), Offset(rx, y - 70f * scale), Offset(rx + 5f * scale, y + 50f * scale), 5f * scale)
    }
}

private fun DrawScope.drawItems(items: List<RunnerItem>) {
    val w = size.width
    val h = size.height
    items.sortedBy { it.depth }.forEach { item ->
        val p = item.depth.coerceIn(0f, 1f)
        val y = h * (.34f + p * .62f)
        val cx = w * (.5f + (item.lane - 1) * (.07f + p * .26f))
        val r = 5f + p * p * 32f
        when (item.type) {
            ItemType.COIN -> {
                drawCircle(Color(0xFFFFD166), r, Offset(cx, y), style = Stroke(width = max(2f, r * .18f)))
                drawCircle(Color(0xFFFFF0A6), r * .58f, Offset(cx, y))
            }
            ItemType.ROCK -> {
                drawRoundRect(Color(0xFF6C7775), Offset(cx - r, y - r * .8f), Size(r * 2f, r * 1.6f), CornerRadius(r * .35f))
                drawRoundRect(Color(0xFF9BA39B), Offset(cx - r * .55f, y - r * .58f), Size(r * .7f, r * .45f), CornerRadius(r * .2f))
            }
            ItemType.GATE -> {
                val gw = r * 2.5f
                drawRoundRect(Color(0xFF8A4739), Offset(cx - gw / 2, y - r * .4f), Size(gw, r * .55f), CornerRadius(r * .12f))
                drawRect(Color(0xFFC17C52), Offset(cx - gw / 2, y - r * .65f), Size(gw, r * .22f))
            }
            ItemType.BRANCH -> {
                drawLine(Color(0xFF6D4B31), Offset(cx - r * 1.5f, y - r * .45f), Offset(cx + r * 1.5f, y - r * .45f), r * .55f)
                drawCircle(Color(0xFF39704A), r * .65f, Offset(cx - r, y - r * .55f))
                drawCircle(Color(0xFF39704A), r * .65f, Offset(cx + r, y - r * .55f))
            }
        }
    }
}

private fun DrawScope.drawRunner(lane: Int, jumping: Boolean, sliding: Boolean, flashing: Boolean) {
    val w = size.width
    val h = size.height
    val x = w * (.5f + (lane - 1) * .333f)
    val baseY = h * NINJA_Y - if (jumping) 85f else 0f
    val scale = if (sliding) .72f else 1f
    val cloak = if (flashing) Color(0xFFFFA4A4) else Color(0xFF7B5CFA)
    drawOval(Color(0x55000000), Offset(x - 32f, h * NINJA_Y + 24f), Size(64f, 16f))
    val scarf = Path().apply {
        moveTo(x - 10f, baseY - 55f * scale); lineTo(x - 47f, baseY - 43f * scale)
        lineTo(x - 17f, baseY - 30f * scale); close()
    }
    drawPath(scarf, Color(0xFFE34C5B))
    drawCircle(Color(0xFF171D2D), 17f * scale, Offset(x, baseY - 67f * scale))
    drawRoundRect(cloak, Offset(x - 23f * scale, baseY - 53f * scale), Size(46f * scale, 51f * scale), CornerRadius(13f))
    drawRoundRect(Color(0xFFE7E9F2), Offset(x - 12f * scale, baseY - 73f * scale), Size(24f * scale, 7f * scale), CornerRadius(4f))
    drawCircle(Color(0xFF101827), 2.8f * scale, Offset(x - 5f * scale, baseY - 69f * scale))
    drawCircle(Color(0xFF101827), 2.8f * scale, Offset(x + 5f * scale, baseY - 69f * scale))
    if (!sliding) {
        drawLine(Color(0xFF171D2D), Offset(x - 10f, baseY - 7f), Offset(x - 18f, baseY + 15f), 8f)
        drawLine(Color(0xFF171D2D), Offset(x + 10f, baseY - 7f), Offset(x + 19f, baseY + 12f), 8f)
    }
}
