package com.destinationcompass.app.ui.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Interactive liquid-glass switch based on AndroidLiquidGlass' LiquidToggle sample. */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current
    val dragWidthPx = with(density) { 20.dp.toPx() }
    val tapSlopPx = with(density) { 6.dp.toPx() }
    val scope = rememberCoroutineScope()
    val fraction = remember { Animatable(if (checked) 1f else 0f, 0.001f) }
    val press = remember { Animatable(0f, 0.001f) }

    LaunchedEffect(checked) {
        fraction.animateTo(
            if (checked) 1f else 0f,
            spring(dampingRatio = 1f, stiffness = 1_000f, visibilityThreshold = 0.001f)
        )
    }

    val gestureModifier = Modifier.pointerInput(enabled, checked, isLtr) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startFraction = fraction.value
            var activePointer = down.id
            var totalDrag = 0f
            scope.launch { press.animateTo(1f, spring(0.6f, 250f, 0.001f)) }

            var released = false
            while (!released) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == activePointer }
                if (change == null) {
                    released = true
                } else if (!change.pressed) {
                    val replacement = event.changes.firstOrNull { it.pressed }
                    if (replacement == null) released = true else activePointer = replacement.id
                } else {
                    totalDrag = change.position.x - down.position.x
                    val directionalDrag = if (isLtr) totalDrag else -totalDrag
                    val draggedFraction =
                        (startFraction + directionalDrag / dragWidthPx).coerceIn(0f, 1f)
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        fraction.snapTo(draggedFraction)
                    }
                }
            }

            val target = if (abs(totalDrag) >= tapSlopPx) fraction.value >= 0.5f else !checked
            if (target != checked) onCheckedChange(target)
            scope.launch {
                launch {
                    fraction.animateTo(
                        if (target) 1f else 0f,
                        spring(dampingRatio = 1f, stiffness = 1_000f, visibilityThreshold = 0.001f)
                    )
                }
                launch { press.animateTo(0f, spring(0.7f, 300f, 0.001f)) }
            }
        }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val trackColor = if (isDark) {
        Color(0xFF787880).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.20f)
    }
    val accentColor = if (isDark) Color(0xFF30D158) else Color(0xFF34C759)
    val trackBackdrop = rememberLayerBackdrop()
    val sampledTrack = rememberBackdrop(trackBackdrop) { drawTrack ->
        val progress = press.value
        scale(
            scaleX = lerpFloat(2f / 3f, 0.75f, progress),
            scaleY = lerpFloat(0f, 0.75f, progress)
        ) {
            drawTrack()
        }
    }
    val thumbBackdrop = rememberCombinedBackdrop(backdrop, sampledTrack)

    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                stateDescription = if (checked) "开启" else "关闭"
                if (!enabled) disabled()
                onClick {
                    if (enabled) {
                        onCheckedChange(!checked)
                        true
                    } else {
                        false
                    }
                }
            }
            .then(gestureModifier)
            .size(64.dp, 40.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(CircleShape)
                .drawBehind { drawRect(lerp(trackColor, accentColor, fraction.value)) }
                .size(64.dp, 28.dp)
        )

        GlassSurface(
            backdrop = thumbBackdrop,
            modifier = Modifier
                .graphicsLayer {
                    val padding = 2.dp.toPx()
                    val travel = 20.dp.toPx()
                    translationX = if (isLtr) {
                        lerpFloat(padding, padding + travel, fraction.value)
                    } else {
                        lerpFloat(-padding, -(padding + travel), fraction.value)
                    }
                }
                .size(40.dp, 24.dp),
            shape = CircleShape,
            quality = GlassQuality.High,
            blurRadius = (8f * (1f - press.value)).dp,
            lensRadius = (5f * press.value).dp,
            lensDepth = (10f * press.value).dp,
            surfaceColor = Color.White.copy(alpha = 1f - press.value),
            layerBlock = {
                val scale = lerpFloat(1f, 1.5f, press.value)
                scaleX = scale
                scaleY = lerpFloat(1f, 1.35f, press.value)
            }
        ) {}
    }
}
