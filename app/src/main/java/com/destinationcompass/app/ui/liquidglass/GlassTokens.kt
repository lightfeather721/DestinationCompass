package com.destinationcompass.app.ui.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

enum class GlassQuality {
    High,
    Balanced,
    Reduced
}

object GlassTokens {
    val CardCornerRadius = 28.dp
    val NavigationCornerRadius = 32.dp
    val BottomSheetCornerRadius = 44.dp
    val ButtonCornerRadius = 50.dp

    // 64 dp navigation surface + 8 dp spacing above and below it. Screens that
    // draw behind the floating navigation bar use this to keep interactive
    // content clear while still allowing the backdrop to extend underneath.
    val NavigationContentClearance = 80.dp

    val DefaultBlurRadius = 4.dp
    val StrongBlurRadius = 8.dp

    val DefaultLensRadius = 24.dp
    val DefaultLensDepth = 48.dp

    const val LightSurfaceAlpha = 0.50f
    const val DarkSurfaceAlpha = 0.22f

    val CardShape = RoundedCornerShape(CardCornerRadius)
    val NavigationShape = RoundedCornerShape(NavigationCornerRadius)
    val BottomSheetShape = RoundedCornerShape(
        topStart = BottomSheetCornerRadius,
        topEnd = BottomSheetCornerRadius
    )
    val ButtonShape = RoundedCornerShape(ButtonCornerRadius)
    val FloatingActionButtonShape = RoundedCornerShape(20.dp)
}
