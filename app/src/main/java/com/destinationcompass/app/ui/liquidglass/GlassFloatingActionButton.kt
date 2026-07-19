package com.destinationcompass.app.ui.liquidglass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun GlassFloatingActionButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    surfaceColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val motion = remember(animationScope) { InteractiveGlassMotion(animationScope) }

    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .size(56.dp),
        shape = GlassTokens.FloatingActionButtonShape,
        quality = if (enabled) GlassQuality.High else GlassQuality.Reduced,
        surfaceColor = surfaceColor,
        surfaceAlphaMultiplier = if (enabled) 1f else 0.5f,
        lensRadius = (18f + 4f * motion.pressProgress).dp,
        lensDepth = (34f + 8f * motion.pressProgress).dp,
        layerBlock = if (enabled) {
            {
                val progress = motion.pressProgress
                val safeSize = size.minDimension.coerceAtLeast(1f)
                val offset = motion.offset
                val scale = lerp(1f, 1f + 5.dp.toPx() / safeSize, progress)
                translationX = safeSize * tanh(0.06f * offset.x / safeSize)
                translationY = safeSize * tanh(0.06f * offset.y / safeSize)

                val dragScale = 5.dp.toPx() / safeSize
                val angle = atan2(offset.y, offset.x)
                scaleX = scale + dragScale * abs(cos(angle) * offset.x / safeSize)
                scaleY = scale + dragScale * abs(sin(angle) * offset.y / safeSize)
            }
        } else {
            null
        },
        overlayModifier = if (enabled) motion.modifier else Modifier,
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}
