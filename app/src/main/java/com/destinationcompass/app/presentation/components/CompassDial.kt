package com.destinationcompass.app.presentation.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinationcompass.app.domain.BearingCalculator
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassDial(
    heading: Float,
    relativeDirection: Float,
    modifier: Modifier = Modifier,
    size: Dp = 316.dp,
    active: Boolean = true,
    showTargetArrow: Boolean = true,
    directionReady: Boolean = true,
    headingReady: Boolean = true
) {
    // Keep an unbounded continuous target so 359° -> 1° animates by +2°, never -358°.
    // animateFloatAsState retargets the running animation without repeatedly cancelling and
    // launching Animatable coroutines for every sensor callback.
    val displayedHeading = rememberContinuousAnimatedAngle(heading, headingReady)
    val displayedDirection = rememberContinuousAnimatedAngle(
        relativeDirection,
        directionReady && showTargetArrow
    )

    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val primary = colorScheme.primary
    val onSurface = colorScheme.onSurface
    val outline = colorScheme.outlineVariant
    val surfaceContainer = colorScheme.surfaceContainerLow
    val arrow = if (active) colorScheme.error else colorScheme.outline
    val density = LocalDensity.current
    val labelPaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textSize = with(density) { 16.sp.toPx() }
        }
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = this.center
            val radius = this.size.minDimension / 2f
            drawCircle(surfaceContainer, radius)
            drawCircle(outline, radius - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            drawCircle(outline.copy(alpha = 0.55f), radius * .78f, style = Stroke(1.dp.toPx()))

            rotate(-displayedHeading, center) {
                repeat(72) { index ->
                    val major = index % 9 == 0
                    val medium = index % 3 == 0
                    val angle = Math.toRadians(index * 5.0 - 90.0)
                    val outer = radius - 14.dp.toPx()
                    val length = when {
                        major -> 15.dp.toPx()
                        medium -> 9.dp.toPx()
                        else -> 5.dp.toPx()
                    }
                    val start = Offset(
                        center.x + cos(angle).toFloat() * (outer - length),
                        center.y + sin(angle).toFloat() * (outer - length)
                    )
                    val end = Offset(
                        center.x + cos(angle).toFloat() * outer,
                        center.y + sin(angle).toFloat() * outer
                    )
                    drawLine(
                        color = if (major) onSurface else outline,
                        start = start,
                        end = end,
                        strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            val labels = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
            val labelRadius = radius - 42.dp.toPx()
            labels.forEach { (text, degrees) ->
                val screenAngle = Math.toRadians((degrees - displayedHeading - 90f).toDouble())
                labelPaint.color = if (text == "N") arrow.toArgb() else onSurface.toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    center.x + cos(screenAngle).toFloat() * labelRadius,
                    center.y + sin(screenAngle).toFloat() * labelRadius + labelPaint.textSize * .35f,
                    labelPaint
                )
            }

            drawCircle(primary.copy(alpha = .12f), 46.dp.toPx(), center)
            drawCircle(colorScheme.surface, 8.dp.toPx(), center)
            drawCircle(primary, 5.dp.toPx(), center)

            if (showTargetArrow) {
                withTransform({ rotate(displayedDirection, center) }) {
                    val tipY = center.y - radius * .63f
                    val tailY = center.y + radius * .34f
                    drawLine(
                        arrow.copy(alpha = .22f),
                        Offset(center.x, tailY),
                        Offset(center.x, tipY + 8.dp.toPx()),
                        strokeWidth = 18.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    val pointer = Path().apply {
                        moveTo(center.x, tipY)
                        lineTo(center.x - 14.dp.toPx(), center.y - 9.dp.toPx())
                        lineTo(center.x - 4.dp.toPx(), center.y - 5.dp.toPx())
                        lineTo(center.x - 4.dp.toPx(), tailY)
                        quadraticTo(center.x, tailY + 8.dp.toPx(), center.x + 4.dp.toPx(), tailY)
                        lineTo(center.x + 4.dp.toPx(), center.y - 5.dp.toPx())
                        lineTo(center.x + 14.dp.toPx(), center.y - 9.dp.toPx())
                        close()
                    }
                    drawPath(pointer, arrow)
                    drawCircle(Color.White, 4.dp.toPx(), center)
                }
            }

            drawArc(
                primary.copy(alpha = .55f),
                startAngle = -104f,
                sweepAngle = 28f,
                useCenter = false,
                topLeft = Offset(center.x - radius + 6.dp.toPx(), center.y - radius + 6.dp.toPx()),
                size = Size((radius - 6.dp.toPx()) * 2, (radius - 6.dp.toPx()) * 2),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun rememberContinuousAnimatedAngle(angle: Float, ready: Boolean): Float {
    val normalized = BearingCalculator.normalizeDegrees(angle)
    var previousNormalized by remember(ready) { mutableFloatStateOf(normalized) }
    var continuousTarget by remember(ready) { mutableFloatStateOf(normalized) }

    LaunchedEffect(normalized, ready) {
        if (!ready) return@LaunchedEffect
        continuousTarget += BearingCalculator.shortestRotation(previousNormalized, normalized)
        previousNormalized = normalized
    }

    return key(ready) {
        val animated by animateFloatAsState(
            targetValue = continuousTarget,
            animationSpec = tween(
                durationMillis = COMPASS_ANIMATION_MILLIS,
                easing = LinearOutSlowInEasing
            ),
            label = "continuous compass angle"
        )
        animated
    }
}

private const val COMPASS_ANIMATION_MILLIS = 100
