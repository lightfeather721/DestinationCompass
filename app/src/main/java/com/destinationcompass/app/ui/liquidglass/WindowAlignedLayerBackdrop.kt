package com.destinationcompass.app.ui.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Corrects LayerBackdrop sampling between the translated Material bottom sheet and
 * the app-level floating navigation bar. LayerBackdrop prefers localPositionOf(),
 * which can report pre-translation coordinates across these two layout branches.
 */
@Stable
class WindowAlignedLayerBackdrop internal constructor(
    val layerBackdrop: LayerBackdrop
) : Backdrop {
    private var sourceCoordinates: LayoutCoordinates? by
        mutableStateOf(null, neverEqualPolicy())

    val sourceModifier: Modifier = Modifier.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) sourceCoordinates = coordinates
    }

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val consumerCoordinates = coordinates ?: return
        val sourceCoordinates = sourceCoordinates ?: return

        val localOffset = try {
            sourceCoordinates.localPositionOf(consumerCoordinates)
        } catch (_: Exception) {
            consumerCoordinates.positionInWindow() - sourceCoordinates.positionInWindow()
        }
        // positionInWindow() resets at a Dialog/Popup window boundary. Screen
        // coordinates stay in the same space, so they also align full-screen
        // glass cards with the application content behind their window.
        val screenOffset =
            consumerCoordinates.positionOnScreen() - sourceCoordinates.positionOnScreen()
        val correction = localOffset - screenOffset

        withTransform({ translate(correction.x, correction.y) }) {
            with(layerBackdrop) {
                drawBackdrop(density, consumerCoordinates, layerBlock)
            }
        }
    }
}

@Composable
fun rememberWindowAlignedLayerBackdrop(): WindowAlignedLayerBackdrop {
    val layerBackdrop = rememberLayerBackdrop()
    return remember(layerBackdrop) { WindowAlignedLayerBackdrop(layerBackdrop) }
}
