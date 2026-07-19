package com.destinationcompass.app.ui.liquidglass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun GlassBottomSheet(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    exportedBackdrop: LayerBackdrop? = null,
    content: @Composable ColumnScope.(contentBackdrop: Backdrop) -> Unit
) {
    val sheetBackdrop = exportedBackdrop ?: rememberLayerBackdrop()

    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth(),
        shape = GlassTokens.BottomSheetShape,
        exportedBackdrop = sheetBackdrop,
        blurRadius = GlassTokens.StrongBlurRadius,
        quality = GlassQuality.High,
        lensRadius = GlassTokens.DefaultLensRadius,
        lensDepth = GlassTokens.DefaultLensDepth
    ) {
        Column(Modifier.fillMaxWidth()) {
            content(sheetBackdrop)
        }
    }
}
