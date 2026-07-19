package com.destinationcompass.app.ui.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Press, highlight and elastic follow motion adapted from AndroidLiquidGlass' LiquidButton. */
internal class InteractiveGlassMotion(
    private val animationScope: CoroutineScope
) {
    private val press = Animatable(0f, 0.001f)
    private val position = Animatable(Offset.Zero, Offset.VectorConverter)
    private var startPosition = Offset.Zero

    val pressProgress: Float get() = press.value
    val offset: Offset get() = position.value - startPosition

    val modifier: Modifier = Modifier
        .drawWithContent {
            val progress = press.value
            if (progress > 0f) {
                val center = Offset(
                    x = position.value.x.coerceIn(0f, size.width),
                    y = position.value.y.coerceIn(0f, size.height)
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.24f * progress),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 1.5f
                    ),
                    blendMode = BlendMode.Plus
                )
            }
            drawContent()
        }
        .pointerInput(animationScope) {
            awaitEachGesture {
                val initialDown = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                startPosition = initialDown.position
                animationScope.launch {
                    launch { press.animateTo(1f, spring(0.5f, 300f, 0.001f)) }
                    launch { position.snapTo(startPosition) }
                }

                var activePointer = initialDown.id
                var released = false
                while (!released) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == activePointer }
                    if (change == null) {
                        released = true
                    } else if (!change.pressed) {
                        val replacement = event.changes.firstOrNull { it.pressed }
                        if (replacement == null) released = true else activePointer = replacement.id
                    } else {
                        animationScope.launch { position.snapTo(change.position) }
                    }
                }

                animationScope.launch {
                    launch { press.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
                    launch { position.animateTo(startPosition, spring(0.5f, 300f)) }
                }
            }
        }
}
