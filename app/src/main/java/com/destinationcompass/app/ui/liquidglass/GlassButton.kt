package com.destinationcompass.app.ui.liquidglass

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun GlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    quality: GlassQuality = GlassQuality.Balanced,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit
) {
    val clickableEnabled = enabled && !loading
    val animationScope = rememberCoroutineScope()
    val motion = remember(animationScope) { InteractiveGlassMotion(animationScope) }

    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = if (clickableEnabled) null else LocalIndication.current,
                enabled = clickableEnabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { if (!clickableEnabled) disabled() }
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp),
        shape = GlassTokens.ButtonShape,
        quality = if (enabled) quality else GlassQuality.Reduced,
        surfaceColor = surfaceColor,
        surfaceAlphaMultiplier = if (enabled) 1f else 0.52f,
        lensRadius = (12f + 6f * motion.pressProgress).dp,
        lensDepth = (24f + 10f * motion.pressProgress).dp,
        layerBlock = if (clickableEnabled) {
            {
                val progress = motion.pressProgress
                val safeHeight = size.height.coerceAtLeast(1f)
                val safeWidth = size.width.coerceAtLeast(1f)
                val scale = lerp(1f, 1f + 4.dp.toPx() / safeHeight, progress)
                val maxOffset = size.minDimension.coerceAtLeast(1f)
                val offset = motion.offset
                translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)

                val maxDragScale = 4.dp.toPx() / safeHeight
                val angle = atan2(offset.y, offset.x)
                scaleX = scale + maxDragScale * abs(cos(angle) * offset.x / size.maxDimension.coerceAtLeast(1f)) *
                    (safeWidth / safeHeight).coerceAtMost(1f)
                scaleY = scale + maxDragScale * abs(sin(angle) * offset.y / size.maxDimension.coerceAtLeast(1f)) *
                    (safeHeight / safeWidth).coerceAtMost(1f)
            }
        } else {
            null
        },
        overlayModifier = if (clickableEnabled) motion.modifier else Modifier,
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp).alpha(if (enabled) 1f else 0.62f),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.defaultMinSize(18.dp, 18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                }
                content()
            }
        }
    }
}
