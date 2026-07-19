package com.destinationcompass.app.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.destinationcompass.app.BuildConfig
import com.destinationcompass.app.model.DistanceUnit
import com.destinationcompass.app.model.LocationRefreshInterval
import com.destinationcompass.app.model.ThemeMode
import com.destinationcompass.app.ui.liquidglass.GlassAlertDialog
import com.destinationcompass.app.ui.liquidglass.GlassButton
import com.destinationcompass.app.ui.liquidglass.GlassCard
import com.destinationcompass.app.ui.liquidglass.GlassQuality
import com.destinationcompass.app.ui.liquidglass.GlassSlider
import com.destinationcompass.app.ui.liquidglass.GlassSurface
import com.destinationcompass.app.ui.liquidglass.GlassTokens
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

private enum class SettingsDialog { THEME, UNIT, LOCATION_REFRESH, CALIBRATION, ABOUT }

@Composable
fun SettingsScreen(
    backdrop: LayerBackdrop,
    dialogBackdrop: Backdrop,
    themeMode: ThemeMode,
    unit: DistanceUnit,
    locationRefreshIntervalMillis: Long,
    sensorAvailable: Boolean,
    onThemeChange: (ThemeMode) -> Unit,
    onUnitChange: (DistanceUnit) -> Unit,
    onLocationRefreshIntervalChange: (Long) -> Unit
) {
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val cardBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(cardBackdrop)
                .background(backgroundColor)
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 120.dp)
        ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "让罗盘符合你的使用习惯",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp, bottom = 18.dp)
            )
        }
        item {
            GlassCard(
                backdrop = cardBackdrop,
                quality = GlassQuality.High,
                blurRadius = GlassTokens.StrongBlurRadius,
                interactive = true,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(Modifier.fillMaxWidth()) {
                SettingRow(cardBackdrop, Icons.Outlined.DarkMode, "主题", themeMode.label()) { dialog = SettingsDialog.THEME }
                HorizontalDivider(Modifier.padding(start = 64.dp))
                SettingRow(cardBackdrop, Icons.Outlined.Straighten, "距离单位", unit.label()) { dialog = SettingsDialog.UNIT }
                HorizontalDivider(Modifier.padding(start = 64.dp))
                SettingRow(
                    cardBackdrop,
                    Icons.Outlined.Schedule,
                    "定位刷新率",
                    "$locationRefreshIntervalMillis ms"
                ) { dialog = SettingsDialog.LOCATION_REFRESH }
                }
            }
            Spacer(Modifier.height(16.dp))
            GlassCard(
                backdrop = cardBackdrop,
                quality = GlassQuality.High,
                blurRadius = GlassTokens.StrongBlurRadius,
                interactive = true,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(Modifier.fillMaxWidth()) {
                SettingRow(
                    cardBackdrop,
                    Icons.Outlined.Explore,
                    "传感器校准",
                    if (sensorAvailable) "磁力计与陀螺仪可用" else "此设备没有方向传感器",
                    enabled = sensorAvailable
                ) { dialog = SettingsDialog.CALIBRATION }
                HorizontalDivider(Modifier.padding(start = 64.dp))
                SettingRow(
                    cardBackdrop,
                    Icons.Outlined.Info,
                    "关于",
                    "Destination Compass ${BuildConfig.VERSION_NAME}"
                ) { dialog = SettingsDialog.ABOUT }
                }
            }
        }
        }
    }

    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            backdrop = dialogBackdrop,
            title = "主题",
            choices = ThemeMode.entries,
            selected = themeMode,
            label = ThemeMode::label,
            onDismiss = { dialog = null },
            onSelected = { onThemeChange(it); dialog = null }
        )
        SettingsDialog.UNIT -> ChoiceDialog(
            backdrop = dialogBackdrop,
            title = "距离单位",
            choices = DistanceUnit.entries,
            selected = unit,
            label = DistanceUnit::label,
            onDismiss = { dialog = null },
            onSelected = { onUnitChange(it); dialog = null }
        )
        SettingsDialog.LOCATION_REFRESH -> RefreshIntervalDialog(
            backdrop = dialogBackdrop,
            intervalMillis = locationRefreshIntervalMillis,
            onDismiss = { dialog = null },
            onConfirm = { onLocationRefreshIntervalChange(it); dialog = null }
        )
        SettingsDialog.CALIBRATION -> GlassAlertDialog(
            backdrop = dialogBackdrop,
            onDismissRequest = { dialog = null },
            icon = { Icon(Icons.Outlined.Explore, null) },
            title = { Text("校准传感器") },
            text = { Text("远离磁铁或金属物体，手持手机在空中缓慢画 8 字 3–5 次。完成后转动手机，确认罗盘刻度连续平滑移动。") },
            confirmButton = {
                GlassButton(backdrop = dialogBackdrop, onClick = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    dialog = null
                }) { Text("检查定位设置") }
            },
            dismissButton = { GlassButton(backdrop = dialogBackdrop, onClick = { dialog = null }) { Text("完成") } }
        )
        SettingsDialog.ABOUT -> GlassAlertDialog(
            backdrop = dialogBackdrop,
            onDismissRequest = { dialog = null },
            icon = { Icon(Icons.Outlined.Explore, null) },
            title = { Text("Destination Compass") },
            text = {
                Text(
                    "一个专注于方向的极简目的地罗盘。\n\n" +
                        "地图：百度地图 Android SDK\n" +
                        "定位：百度定位 Android SDK\n" +
                        "界面：Jetpack Compose + Material 3\n\n" +
                        "Liquid Glass：感谢 Kyant0/AndroidLiquidGlass 开源项目\n\n" +
                        "made by liu21"
                )
            },
            confirmButton = { GlassButton(backdrop = dialogBackdrop, onClick = { dialog = null }) { Text("知道了") } }
        )
        null -> Unit
    }
}

@Composable
private fun SettingRow(
    backdrop: LayerBackdrop,
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(summary) },
        leadingContent = {
            GlassSurface(
                backdrop = backdrop,
                shape = MaterialTheme.shapes.large,
                quality = if (enabled) GlassQuality.High else GlassQuality.Reduced,
                surfaceColor = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.18f else 0.08f)
            ) {
                Icon(
                    icon,
                    null,
                    modifier = Modifier.padding(9.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        },
        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(
            interactionSource = null,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    )
}

@Composable
private fun <T> ChoiceDialog(
    backdrop: Backdrop,
    title: String,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit
) {
    GlassAlertDialog(
        backdrop = backdrop,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                choices.forEach { value ->
                    val isSelected = selected == value
                    GlassButton(
                        onClick = { onSelected(value) },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                        quality = GlassQuality.High,
                        surfaceColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ) {
                        Text(label(value), fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                        if (isSelected) Icon(Icons.Filled.Check, null)
                    }
                }
            }
        },
        confirmButton = { GlassButton(backdrop = backdrop, onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RefreshIntervalDialog(
    backdrop: Backdrop,
    intervalMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var sliderValue by remember(intervalMillis) { mutableFloatStateOf(intervalMillis.toFloat()) }
    val normalized = LocationRefreshInterval.normalize(sliderValue.roundToInt().toLong())
    GlassAlertDialog(
        backdrop = backdrop,
        onDismissRequest = onDismiss,
        title = { Text("定位刷新率") },
        text = {
            Column {
                Text(
                    "$normalized ms",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                GlassSlider(
                    value = normalized.toFloat(),
                    onValueChange = {
                        sliderValue = LocationRefreshInterval.normalize(it.roundToInt().toLong()).toFloat()
                    },
                    valueRange = LocationRefreshInterval.MIN_MILLIS.toFloat()..LocationRefreshInterval.MAX_MILLIS.toFloat(),
                    backdrop = backdrop,
                    steps = ((LocationRefreshInterval.MAX_MILLIS - LocationRefreshInterval.MIN_MILLIS) /
                        LocationRefreshInterval.STEP_MILLIS).toInt() - 1,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("500 ms", style = MaterialTheme.typography.labelMedium)
                    Text("5000 ms", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "百度定位周期最低约 1000 ms；选择 500–900 ms 时会按 1000 ms 获取定位，并保持连续动画。静止时会自动降至 5000 ms。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        },
        confirmButton = { GlassButton(backdrop = backdrop, onClick = { onConfirm(normalized) }) { Text("应用") } },
        dismissButton = { GlassButton(backdrop = backdrop, onClick = onDismiss) { Text("取消") } }
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun DistanceUnit.label(): String = when (this) {
    DistanceUnit.KILOMETERS -> "公里"
    DistanceUnit.MILES -> "英里"
}
