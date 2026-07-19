package com.destinationcompass.app.ui.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

/** Liquid-glass slider adapted from AndroidLiquidGlass' interactive slider sample. */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true
) {
    val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f, 0.001f) }
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val trackBackdrop = rememberLayerBackdrop()
    val sampledTrack = rememberBackdrop(trackBackdrop) { drawTrack -> drawTrack() }
    val thumbBackdrop = rememberCombinedBackdrop(backdrop, sampledTrack)
    val thumbWidth = 40.dp
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
    val accentColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                setProgress { target ->
                    if (!enabled) return@setProgress false
                    currentOnValueChange.value(target.coerceIn(valueRange))
                    true
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()
        val thumbWidthPx = with(density) { thumbWidth.toPx() }

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(enabled, trackWidthPx, isLtr, valueRange) {
                    if (!enabled || trackWidthPx <= 0f) return@pointerInput

                    fun updateValue(positionX: Float) {
                        val rawFraction = (positionX / trackWidthPx).coerceIn(0f, 1f)
                        val directedFraction = if (isLtr) rawFraction else 1f - rawFraction
                        currentOnValueChange.value(valueRange.start + range * directedFraction)
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        scope.launch { press.animateTo(1f, spring(0.55f, 300f, 0.001f)) }
                        updateValue(down.position.x)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            updateValue(change.position.x)
                        }
                        scope.launch { press.animateTo(0f, spring(0.7f, 320f, 0.001f)) }
                    }
                }
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .layerBackdrop(trackBackdrop)
                .background(trackColor)
        ) {
            Box(
                Modifier
                    .align(if (isLtr) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(accentColor)
            )
        }

        GlassSurface(
            backdrop = thumbBackdrop,
            modifier = Modifier
                .graphicsLayer {
                    translationX = (trackWidthPx - thumbWidthPx).coerceAtLeast(0f) *
                        if (isLtr) fraction else 1f - fraction
                }
                .size(thumbWidth, 24.dp),
            shape = CircleShape,
            quality = GlassQuality.High,
            blurRadius = (8f * (1f - press.value)).dp,
            lensRadius = (5f + 8f * press.value).dp,
            lensDepth = (10f + 16f * press.value).dp,
            surfaceColor = Color.White.copy(alpha = lerp(0.92f, 0.26f, press.value)),
            layerBlock = {
                scaleX = lerp(1f, 1.5f, press.value)
                scaleY = lerp(1f, 1.34f, press.value)
                clip = true
                shape = CircleShape
            }
        ) {}
    }
}
