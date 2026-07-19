package com.destinationcompass.app.ui.liquidglass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassAlertDialog(
    backdrop: Backdrop,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties
    ) {
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
            quality = GlassQuality.High,
            blurRadius = GlassTokens.StrongBlurRadius,
            enableLens = true
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                icon?.let { content ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { content() }
                }
                title?.let { content ->
                    androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.headlineSmall) { content() }
                }
                text?.let { content ->
                    androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium) { content() }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
