package com.destinationcompass.app.ui.liquidglass

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Motion model adapted from AndroidLiquidGlass' DampedDragAnimation sample. */
internal class LiquidTabMotion(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    internal val valueRange: ClosedFloatingPointRange<Float>,
    private val pressedScale: Float,
    private val onDragStopped: LiquidTabMotion.() -> Unit,
    private val onDrag: LiquidTabMotion.(dragAmount: Offset) -> Unit
) {
    private val valueAnimation = Animatable(initialValue, 0.001f)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(1f, 0.001f)
    private val scaleYAnimation = Animatable(1f, 0.001f)
    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    private val valueSpec = spring<Float>(dampingRatio = 1f, stiffness = 1_000f, visibilityThreshold = 0.001f)
    private val velocitySpec = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.01f)
    private val pressSpec = spring<Float>(dampingRatio = 1f, stiffness = 1_000f, visibilityThreshold = 0.001f)
    private val scaleXSpec = spring<Float>(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val scaleYSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val tapMoveSpec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    private val tapPressSpec = spring<Float>(dampingRatio = 1f, stiffness = 1_200f, visibilityThreshold = 0.001f)
    private val tapReleaseSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 1_000f, visibilityThreshold = 0.001f)

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectLiquidTabDragGestures(
            onDragStart = { press(pressedScale) },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            },
            onDrag = { _, dragAmount -> onDrag(dragAmount) }
        )
    }

    private fun press(pressedScale: Float) {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressAnimation.animateTo(1f, pressSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYSpec) }
        }
    }

    private fun release() {
        animationScope.launch {
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressAnimation.animateTo(0f, pressSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.snapTo(target)
            updateVelocity()
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                val target = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(target, valueSpec) { updateVelocity() } }
                if (velocity != 0f) launch { velocityAnimation.animateTo(0f, velocitySpec) }
            }
        }
    }

    fun animateTapToValue(value: Float) {
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutatorMutex.mutate {
                velocityTracker.resetTracking()
                val target = value.coerceIn(valueRange)

                // Grow quickly and remain fully pressed while the capsule travels.
                coroutineScope {
                    val pressJob = launch { pressAnimation.animateTo(1f, tapPressSpec) }
                    val scaleXJob = launch { scaleXAnimation.animateTo(pressedScale, tapPressSpec) }
                    val scaleYJob = launch { scaleYAnimation.animateTo(pressedScale, tapPressSpec) }

                    // Position completion is the release trigger. Do not wait for the press
                    // springs to finish converging after the capsule has visually arrived.
                    valueAnimation.animateTo(target, tapMoveSpec) { updateVelocity() }
                    pressJob.cancelAndJoin()
                    scaleXJob.cancelAndJoin()
                    scaleYJob.cancelAndJoin()
                }

                // Reaching the destination is the release point: restore immediately.
                coroutineScope {
                    launch { pressAnimation.animateTo(0f, tapReleaseSpec) }
                    launch { scaleXAnimation.animateTo(1f, tapReleaseSpec) }
                    launch { scaleYAnimation.animateTo(1f, tapReleaseSpec) }
                    launch { velocityAnimation.animateTo(0f, tapReleaseSpec) }
                }
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
        val targetVelocity = velocityTracker.calculateVelocity().x / range
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocitySpec) }
    }
}

private suspend fun PointerInputScope.inspectLiquidTabDragGestures(
    onDragStart: (PointerInputChange) -> Unit,
    onDragEnd: (PointerInputChange) -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val up = dragUntilUp(initialDown.id) { change ->
            onDrag(change, change.positionChange())
        }
        if (up == null) onDragCancel() else onDragEnd(up)
    }
}

private suspend inline fun AwaitPointerEventScope.dragUntilUp(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    if (currentEvent.changes.firstOrNull { it.id == pointerId }?.pressed != true) return null
    var activePointer = pointerId
    while (true) {
        val change = awaitDragOrUp(activePointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        activePointer = change.id
    }
}

private suspend fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var activePointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == activePointer } ?: return null
        if (change.changedToUpIgnoreConsumed()) {
            val replacement = event.changes.firstOrNull { it.pressed } ?: return change
            activePointer = replacement.id
        } else if (change.previousPosition != change.position) {
            return change
        }
    }
}
