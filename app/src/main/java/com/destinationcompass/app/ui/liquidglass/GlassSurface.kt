package com.destinationcompass.app.ui.liquidglass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = GlassTokens.CardShape,
    exportedBackdrop: LayerBackdrop? = null,
    blurRadius: Dp = GlassTokens.DefaultBlurRadius,
    lensRadius: Dp = GlassTokens.DefaultLensRadius,
    lensDepth: Dp = GlassTokens.DefaultLensDepth,
    enableLens: Boolean = true,
    quality: GlassQuality = GlassQuality.Balanced,
    surfaceColor: Color = Color.Unspecified,
    surfaceAlphaMultiplier: Float = 1f,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    overlayModifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resolvedSurfaceColor = if (surfaceColor.isSpecified) {
        surfaceColor
    } else if (isDark) {
        Color.Black.copy(alpha = GlassTokens.DarkSurfaceAlpha * surfaceAlphaMultiplier)
    } else {
        Color.White.copy(alpha = GlassTokens.LightSurfaceAlpha * surfaceAlphaMultiplier)
    }
    val surfacePaint = remember(resolvedSurfaceColor) {
        Paint().apply { color = resolvedSurfaceColor }
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = if (isDark) 0.98f else 0.96f
        )
        Box(
            modifier = (if (layerBlock != null) modifier.graphicsLayer(layerBlock) else modifier)
                .clip(shape)
                .background(fallbackColor)
                .then(overlayModifier),
            contentAlignment = contentAlignment,
            content = content
        )
        return
    }

    val glassModifier = modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            when (quality) {
                GlassQuality.High -> {
                    vibrancy()
                    blur(blurRadius.toPx())
                    if (enableLens) lens(lensRadius.toPx(), lensDepth.toPx())
                }

                GlassQuality.Balanced -> {
                    vibrancy()
                    blur(blurRadius.toPx())
                    if (enableLens) lens(lensRadius.toPx() * 0.72f, lensDepth.toPx() * 0.58f)
                }

                GlassQuality.Reduced -> blur((blurRadius * 0.5f).toPx())
            }
        },
        layerBlock = layerBlock,
        exportedBackdrop = exportedBackdrop,
        onDrawSurface = {
            drawContext.canvas.drawOutline(
                outline = shape.createOutline(size, layoutDirection, this),
                paint = surfacePaint
            )
        }
    )
        // Interaction highlights are drawn after the backdrop layer so they stay
        // visible, but must remain inside the glass silhouette.
        .then(Modifier.clip(shape))
        .then(overlayModifier)

    Box(
        modifier = glassModifier,
        contentAlignment = contentAlignment,
        content = content
    )
}
