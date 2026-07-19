package com.destinationcompass.app.ui.liquidglass

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = GlassTokens.CardShape,
    exportedBackdrop: LayerBackdrop? = null,
    quality: GlassQuality = GlassQuality.Balanced,
    blurRadius: Dp = GlassTokens.DefaultBlurRadius,
    surfaceColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val motion = remember(animationScope) { InteractiveGlassMotion(animationScope) }
    val animateInteraction = interactive && enabled
    val clipShape = shape
    val interactiveModifier = if (onClick != null) {
        modifier.clickable(
            interactionSource = interactionSource,
            indication = if (animateInteraction) null else LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
    } else {
        modifier
    }

    GlassSurface(
        backdrop = backdrop,
        modifier = interactiveModifier,
        shape = shape,
        exportedBackdrop = exportedBackdrop,
        quality = quality,
        blurRadius = blurRadius,
        surfaceColor = surfaceColor,
        layerBlock = if (animateInteraction) {
            {
                // Clip the transformed glass layer itself. Clipping outside
                // drawBackdrop leaves its rectangular blur buffer visible in
                // rounded corners, especially on strongly tinted cards.
                clip = true
                this.shape = clipShape
                val progress = motion.pressProgress
                val safeHeight = size.height.coerceAtLeast(1f)
                val safeWidth = size.width.coerceAtLeast(1f)
                val scale = lerp(1f, 1f + 4.dp.toPx() / size.minDimension.coerceAtLeast(1f), progress)
                val maxOffset = size.minDimension.coerceAtLeast(1f)
                val offset = motion.offset
                translationX = maxOffset * tanh(0.04f * offset.x / maxOffset)
                translationY = maxOffset * tanh(0.04f * offset.y / maxOffset)

                val maxDragScale = 3.dp.toPx() / safeHeight
                val angle = atan2(offset.y, offset.x)
                scaleX = scale + maxDragScale * abs(cos(angle) * offset.x / size.maxDimension.coerceAtLeast(1f)) *
                    (safeWidth / safeHeight).coerceAtMost(1f)
                scaleY = scale + maxDragScale * abs(sin(angle) * offset.y / size.maxDimension.coerceAtLeast(1f)) *
                    (safeHeight / safeWidth).coerceAtMost(1f)
            }
        } else {
            null
        },
        overlayModifier = if (animateInteraction) motion.modifier else Modifier,
        enableLens = quality != GlassQuality.Reduced
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}
