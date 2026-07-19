package com.destinationcompass.app.ui.liquidglass

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

data class GlassNavigationItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * Port of AndroidLiquidGlass' LiquidBottomTabs layering model.
 *
 * The invisible duplicate row is recorded into [tabsBackdrop]. The moving selection capsule
 * samples a combination of the page and that row, so icons and labels stay visible inside the
 * refractive selection instead of being covered by a tinted rectangle.
 */
@Composable
fun GlassNavigationBar(
    backdrop: Backdrop,
    selectedIndex: Int,
    items: List<GlassNavigationItem>,
    onItemSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    require(items.isNotEmpty()) { "items must not be empty" }

    val selected = selectedIndex.coerceIn(items.indices)
    val isLight = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val accentColor = MaterialTheme.colorScheme.primary
    val contentMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (isLight) {
        Color(0xFFFAFAFA).copy(alpha = 0.28f)
    } else {
        Color(0xFF121212).copy(alpha = 0.28f)
    }
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val horizontalInsetPx = with(density) { 4.dp.toPx() }
        val tabWidthPx = (constraints.maxWidth.toFloat() - horizontalInsetPx * 2f) / items.size
        val panelDrag = remember { Animatable(0f) }
        val panelOffsetPx by remember(density) {
            derivedStateOf {
                val fraction = (panelDrag.value / constraints.maxWidth.coerceAtLeast(1))
                    .coerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val tabMotion = remember(animationScope, items.size, tabWidthPx, isLtr) {
            LiquidTabMotion(
                animationScope = animationScope,
                initialValue = selected.toFloat(),
                valueRange = 0f..items.lastIndex.toFloat(),
                pressedScale = 78f / 56f,
                onDragStopped = {
                    val target = targetValue.roundToInt().coerceIn(items.indices)
                    animateToValue(target.toFloat())
                    currentOnItemSelected(target)
                    animationScope.launch {
                        panelDrag.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f))
                    }
                },
                onDrag = { dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(valueRange)
                    )
                    animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        panelDrag.snapTo(panelDrag.value + dragAmount.x)
                    }
                }
            )
        }

        LaunchedEffect(selected, tabMotion) {
            if (tabMotion.targetValue != selected.toFloat()) {
                tabMotion.animateToValue(selected.toFloat())
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            LegacyGlassTabs(
                backdrop = backdrop,
                items = items,
                selected = selected,
                onItemSelected = { index ->
                    tabMotion.animateTapToValue(index.toFloat())
                    currentOnItemSelected(index)
                },
                tabMotion = tabMotion,
                tabWidthPx = tabWidthPx,
                panelOffsetPx = panelOffsetPx,
                isLtr = isLtr,
                accentColor = accentColor,
                contentMuted = contentMuted
            )
            return@BoxWithConstraints
        }

        // Visible glass bar and its normal, accessible tab content.
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = panelOffsetPx }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { GlassTokens.NavigationShape },
                    effects = {
                        vibrancy()
                        blur(16.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val maxScale = (size.width + 16.dp.toPx()) / size.width.coerceAtLeast(1f)
                        val scale = 1f + (maxScale - 1f) * tabMotion.pressProgress
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .padding(4.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItems(
                items = items,
                selected = selected,
                onItemSelected = { index ->
                    tabMotion.animateTapToValue(index.toFloat())
                    currentOnItemSelected(index)
                },
                accentColor = accentColor,
                contentMuted = contentMuted,
                contentScale = { 1f },
                contentAlpha = { index ->
                    ((abs(index - tabMotion.value) - 0.55f) / 0.35f).coerceIn(0f, 1f)
                },
                interactive = true
            )
        }

        // Record a tinted copy of the icons and labels for the selection capsule to refract.
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffsetPx }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { GlassTokens.NavigationShape },
                    effects = {
                        val progress = tabMotion.pressProgress
                        vibrancy()
                        blur(16.dp.toPx())
                        lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                    },
                    highlight = { Highlight.Default.copy(alpha = tabMotion.pressProgress) },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .height(56.dp)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItems(
                items = items,
                selected = selected,
                onItemSelected = {},
                accentColor = accentColor,
                contentMuted = contentMuted,
                contentScale = { 1f + 0.2f * tabMotion.pressProgress },
                contentAlpha = { 1f },
                interactive = false
            )
        }

        // The selection only refracts the page + recorded content; it never paints over labels.
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (isLtr) {
                        tabMotion.value * tabWidthPx + panelOffsetPx
                    } else {
                        -tabMotion.value * tabWidthPx + panelOffsetPx
                    }
                }
                .then(tabMotion.modifier)
                .drawBackdrop(
                    backdrop = combinedBackdrop,
                    shape = { GlassTokens.ButtonShape },
                    effects = {
                        val progress = tabMotion.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = tabMotion.pressProgress) },
                    shadow = { Shadow(alpha = tabMotion.pressProgress) },
                    innerShadow = {
                        InnerShadow(
                            radius = 8.dp * tabMotion.pressProgress,
                            alpha = tabMotion.pressProgress
                        )
                    },
                    layerBlock = {
                        val velocity = (tabMotion.velocity / 10f).coerceIn(-0.2f, 0.2f)
                        scaleX = tabMotion.scaleX / (1f - velocity * 0.75f)
                        scaleY = tabMotion.scaleY * (1f - velocity * 0.25f)
                    },
                    onDrawSurface = {
                        val progress = tabMotion.pressProgress
                        drawRect(
                            if (isLight) Color.Black.copy(alpha = 0.10f)
                            else Color.White.copy(alpha = 0.10f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / items.size)
        )
    }
}

@Composable
private fun LegacyGlassTabs(
    backdrop: Backdrop,
    items: List<GlassNavigationItem>,
    selected: Int,
    onItemSelected: (Int) -> Unit,
    tabMotion: LiquidTabMotion,
    tabWidthPx: Float,
    panelOffsetPx: Float,
    isLtr: Boolean,
    accentColor: Color,
    contentMuted: Color
) {
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxSize(),
        shape = GlassTokens.NavigationShape,
        quality = GlassQuality.Reduced
    ) {
        Row(
            Modifier.fillMaxSize().padding(4.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItems(
                items = items,
                selected = selected,
                onItemSelected = onItemSelected,
                accentColor = accentColor,
                contentMuted = contentMuted,
                contentScale = { 1f },
                contentAlpha = { 1f },
                interactive = true
            )
        }
    }
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .graphicsLayer {
                translationX = if (isLtr) {
                    tabMotion.value * tabWidthPx + panelOffsetPx
                } else {
                    -tabMotion.value * tabWidthPx + panelOffsetPx
                }
                val velocity = (tabMotion.velocity / 10f).coerceIn(-0.2f, 0.2f)
                scaleX = tabMotion.scaleX / (1f - velocity * 0.75f)
                scaleY = tabMotion.scaleY * (1f - velocity * 0.25f)
            }
            .then(tabMotion.modifier)
            .height(56.dp)
            .fillMaxWidth(1f / items.size)
            .clip(GlassTokens.ButtonShape)
            .background(accentColor.copy(alpha = 0.08f))
            .border(1.dp, accentColor.copy(alpha = 0.16f), GlassTokens.ButtonShape)
    )
}

@Composable
private fun RowScope.NavigationItems(
    items: List<GlassNavigationItem>,
    selected: Int,
    onItemSelected: (Int) -> Unit,
    accentColor: Color,
    contentMuted: Color,
    contentScale: () -> Float,
    contentAlpha: (index: Int) -> Float,
    interactive: Boolean
) {
    items.forEachIndexed { index, item ->
        val isSelected = index == selected
        val color by animateColorAsState(
            targetValue = if (isSelected) accentColor else contentMuted,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
            label = "Tab content color"
        )
        val interactionSource = remember { MutableInteractionSource() }
        val interactionModifier = if (interactive) {
            Modifier.selectable(
                selected = isSelected,
                onClick = { onItemSelected(index) },
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null
            )
        } else {
            Modifier
        }

        Column(
            Modifier
                .clip(GlassTokens.ButtonShape)
                .then(interactionModifier)
                .fillMaxHeight()
                .weight(1f)
                .graphicsLayer {
                    val scale = contentScale()
                    scaleX = scale
                    scaleY = scale
                    alpha = contentAlpha(index)
                },
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = if (interactive) item.label else null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = item.label,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
