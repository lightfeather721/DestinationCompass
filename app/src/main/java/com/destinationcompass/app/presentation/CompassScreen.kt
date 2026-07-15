package com.destinationcompass.app.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.destinationcompass.app.data.location.LocationState
import com.destinationcompass.app.data.location.MotionState
import com.destinationcompass.app.model.DistanceUnit
import com.destinationcompass.app.presentation.components.CompassDial
import com.destinationcompass.app.domain.BearingCalculator
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(viewModel: MainViewModel, onChooseDestination: () -> Unit) {
    val destination by viewModel.destination.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val unit by viewModel.distanceUnit.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val compassState by viewModel.compassState.collectAsState()
    val context = LocalContext.current
    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasPreciseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> viewModel.onLocationPermissionResult(result.values.any { it }) }
    val destinationProximity = if (metrics.isDirectionReliable) {
        destinationProximityForDistance(metrics.distanceMeters)
    } else {
        DestinationProximity.FAR
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        Surface(
            onClick = onChooseDestination,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Filled.LocationOn, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("当前目标", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(destination?.name ?: "选择目的地", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Filled.ChevronRight, "重新选择目标")
            }
        }

        Spacer(Modifier.height(24.dp))
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CompassDial(
                heading = metrics.heading,
                relativeDirection = metrics.relativeDirection,
                active = metrics.isDirectionReliable,
                size = minOf(maxWidth, 316.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            when {
                metrics.isDirectionReliable -> "沿箭头方向前进"
                locationState.hasFix -> "方向更新已暂停，等待可靠数据"
                else -> "正在确定你的位置…"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Outlined.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "当前速度 ${formatSpeed(locationState)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        AnimatedVisibility(
            visible = destinationProximity != DestinationProximity.FAR,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(Modifier.padding(top = 10.dp)) {
                DestinationProximityNotice(
                    proximity = destinationProximity,
                    distanceMeters = metrics.distanceMeters
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Metric("距离", formatDistance(metrics.distanceMeters, unit), Modifier.weight(1f))
                Metric("方向", if (metrics.distanceMeters == null) "--" else "${BearingCalculator.directionName(metrics.bearing)} ${metrics.bearing.toInt()}°", Modifier.weight(1f))
                Metric("预计步行", metrics.distanceMeters?.let { "${BearingCalculator.walkingMinutes(it)} 分钟" } ?: "--", Modifier.weight(1f))
            }
        }

        LocationStatusCard(
            state = locationState,
            hasPermission = hasLocationPermission,
            hasPrecisePermission = hasPreciseLocation,
            onRequestPermission = {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onOpenLocationSettings = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        )

        AnimatedVisibility(
            visible = compassState.calibrationRequired || !compassState.isAvailable,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Explore, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (compassState.isAvailable) "磁场异常，需要校准" else "设备缺少方向传感器",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            if (compassState.isAvailable) "请远离金属物体，并缓慢旋转手机画 8 字" else "无法提供实时指南针方向",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LocationStatusCard(
    state: LocationState,
    hasPermission: Boolean,
    hasPrecisePermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val accurate = hasPermission && hasPrecisePermission && state.isLocationEnabled && state.isAccurate
    val containerColor = if (accurate) {
        if (dark) Color(0xFF173D25) else Color(0xFFE0F5E6)
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (accurate) {
        if (dark) Color(0xFFB7F2C7) else Color(0xFF135D2B)
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val title = when {
        !hasPermission -> "需要定位权限"
        !hasPrecisePermission -> "需要精确位置权限"
        !state.isLocationEnabled -> "GPS 已关闭"
        !state.hasFix -> "正在获取实时定位"
        !state.isValid -> "GPS 信号较弱"
        else -> "GPS 精准"
    }
    val accuracy = state.accuracyMeters?.let { "精度 ±${it.roundToInt()} m" } ?: "等待定位数据"
    val detail = when {
        !hasPermission -> "授权后才能持续计算目标方向"
        !hasPrecisePermission -> "当前为模糊定位，方向更新已暂停"
        !state.isLocationEnabled -> "开启系统定位后自动恢复"
        state.hasFix && !state.isValid -> "$accuracy · 已暂停方向更新"
        state.isValid -> "$accuracy · ${state.motionState.label()} · ${formatUpdateInterval(state.updateIntervalMillis)} 刷新"
        else -> "正在请求高精度 GPS 数据"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    accurate -> Icons.Filled.GpsFixed
                    state.isLocationEnabled -> Icons.Outlined.WarningAmber
                    else -> Icons.Outlined.GpsOff
                },
                null,
                tint = contentColor
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = contentColor)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = contentColor)
            }
            when {
                !hasPermission || !hasPrecisePermission -> TextButton(onClick = onRequestPermission) { Text("授权") }
                !state.isLocationEnabled -> TextButton(onClick = onOpenLocationSettings) { Text("开启") }
            }
        }
    }
}

private fun MotionState.label(): String = when (this) {
    MotionState.STATIONARY -> "静止"
    MotionState.WALKING -> "步行"
    MotionState.FAST -> "高速移动"
}

private fun formatUpdateInterval(milliseconds: Long): String =
    if (milliseconds % 1_000L == 0L) "${milliseconds / 1_000L} 秒" else String.format(Locale.US, "%.2f 秒", milliseconds / 1_000.0)

private fun formatSpeed(state: LocationState): String =
    if (state.hasFix && state.isValid) {
        String.format(Locale.getDefault(), "%.1f km/h", (state.speedMetersPerSecond * 3.6f).coerceAtLeast(0f))
    } else {
        "-- km/h"
    }

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
    }
}

private fun formatDistance(meters: Double?, unit: DistanceUnit): String {
    meters ?: return "--"
    return if (unit == DistanceUnit.KILOMETERS) {
        if (meters < 1000) "${meters.toInt()} m" else String.format(Locale.getDefault(), "%.1f km", meters / 1000)
    } else {
        val miles = meters / 1609.344
        if (miles < .1) "${(meters * 1.09361).toInt()} yd" else String.format(Locale.getDefault(), "%.1f mi", miles)
    }
}
