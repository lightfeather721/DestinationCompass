package com.destinationcompass.app.presentation

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.Circle
import com.baidu.mapapi.map.CircleOptions
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.TextureMapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.map.Stroke
import com.baidu.mapapi.model.LatLng
import com.destinationcompass.app.data.map.MapService
import com.destinationcompass.app.data.map.BaiduMapSdkInitializer
import com.destinationcompass.app.data.location.LocationState
import com.destinationcompass.app.data.location.MotionState
import com.destinationcompass.app.data.location.GPS_WEAK_SIGNAL_THRESHOLD_METERS
import com.destinationcompass.app.domain.BearingCalculator
import com.destinationcompass.app.model.Destination
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    initialDestination: Destination?,
    hasLocationPermission: Boolean,
    hasPreciseLocation: Boolean,
    locationState: LocationState,
    userHeading: Float,
    isOnline: Boolean,
    followMyLocation: Boolean,
    headingUp: Boolean,
    mapZoomLevel: Float,
    favoriteIds: Set<String>,
    onLocationPermissionResult: (Boolean) -> Unit,
    onFollowMyLocationChange: (Boolean) -> Unit,
    onHeadingUpChange: (Boolean) -> Unit,
    onMapZoomLevelChange: (Float) -> Unit,
    onFavoriteToggle: (Destination, Boolean) -> Unit,
    onClearDestination: () -> Unit,
    onConfirm: (Destination) -> Unit
) {
    val context = LocalContext.current
    BaiduMapSdkInitializer.ensureInitialized(context)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceContainerHighColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val darkSurface = surfaceColor.luminance() < 0.5f
    val glassHighlightColor = Color.White.copy(alpha = if (darkSurface) 0.16f else 0.62f)
    val searchShape = RoundedCornerShape(20.dp)
    val blurOverlayColor = surfaceContainerHighColor.copy(alpha = if (darkSurface) 0.34f else 0.46f)
    val destinationLineColor = Color(0xFF0B57D0).toArgb()
    val mapView = remember { TextureMapView(context) }
    val mapBlurTarget = remember(mapView) {
        BlurTarget(context).apply {
            addView(
                mapView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }
    val mapService = remember { MapService() }
    val markerAnimator = remember { MapMarkerAnimator() }
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)
    val scope = rememberCoroutineScope()
    var selected by remember(initialDestination) { mutableStateOf(initialDestination) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var marker by remember { mutableStateOf<Marker?>(null) }
    var currentLocationMarker by remember { mutableStateOf<Marker?>(null) }
    var currentAccuracyCircle by remember { mutableStateOf<Circle?>(null) }
    var currentDestinationLine by remember { mutableStateOf<Polyline?>(null) }
    var selectionRevision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val destinationDistanceMeters = selected?.takeIf { locationState.isValid }?.let { destination ->
        val latitude = locationState.latitude
        val longitude = locationState.longitude
        if (latitude != null && longitude != null) {
            BearingCalculator.distanceMeters(
                latitude,
                longitude,
                destination.latitude,
                destination.longitude
            )
        } else null
    }
    val destinationProximity = destinationProximityForDistance(destinationDistanceMeters)
    val selectedIsFavorite = selected?.id?.let { it in favoriteIds } ?: false
    val useMovementHeading = shouldUseMovementHeading(locationState)
    val navigationHeading = if (useMovementHeading) locationState.bearingDegrees!! else userHeading
    val navigationHeadingStep = (navigationHeading / 2f).roundToInt() * 2f
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> onLocationPermissionResult(result.values.any { it }) }

    fun showDestination(destination: Destination, zoom: Float = 17f) {
        selectionRevision += 1
        selected = destination
        val point = LatLng(destination.latitude, destination.longitude)
        marker?.remove()
        marker = mapView.map.addOverlay(
            MarkerOptions()
                .position(point)
                .icon(createDestinationMarker(context))
        ) as Marker
        currentLocationMarker?.position?.let { userPoint ->
            currentDestinationLine?.points = listOf(userPoint, point)
        }
        mapView.map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(point, zoom))
    }

    fun clearDestinationSelection() {
        selectionRevision += 1
        loading = false
        error = null
        searchResults = emptyList()
        marker?.remove()
        marker = null
        currentDestinationLine?.remove()
        currentDestinationLine = null
        selected = null
        onClearDestination()
        scope.launch { bottomSheetState.expand() }
    }

    fun performSearch() {
        if (query.isBlank()) return
        if (!isOnline) {
            error = "网络不可用，连接网络后才能搜索地点"
            return
        }
        loading = true
        error = null
        searchResults = emptyList()
        mapService.searchPlace(query) { result ->
            loading = false
            result.onSuccess {
                searchResults = it
                if (it.isEmpty()) error = "没有找到相关地点"
            }.onFailure { error = it.message }
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            markerAnimator.cancel()
            mapService.destroy()
            onMapZoomLevelChange(mapView.map.mapStatus.zoom)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.showZoomControls(false)
        mapView.showScaleControl(true)
        mapView.map.uiSettings.apply {
            isCompassEnabled = true
            isRotateGesturesEnabled = true
        }
        mapView.map.setCompassEnable(true)
        mapView.map.setCompassPosition(Point((20 * context.resources.displayMetrics.density).roundToInt(), (104 * context.resources.displayMetrics.density).roundToInt()))
        mapView.map.isMyLocationEnabled = false
        mapView.map.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(status: MapStatus?) = Unit
            override fun onMapStatusChangeStart(status: MapStatus?, reason: Int) = Unit
            override fun onMapStatusChange(status: MapStatus?) = Unit
            override fun onMapStatusChangeFinish(status: MapStatus?) {
                status?.let { onMapZoomLevelChange(it.zoom) }
            }
        })
        mapView.map.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
            override fun onMapClick(point: LatLng) {
                selectionRevision += 1
                val requestRevision = selectionRevision
                loading = true
                error = null
                searchResults = emptyList()
                onFollowMyLocationChange(false)
                scope.launch { bottomSheetState.expand() }
                marker?.remove()
                marker = mapView.map.addOverlay(
                    MarkerOptions().position(point).icon(createDestinationMarker(context))
                ) as Marker
                selected = Destination(name = "地图选点", latitude = point.latitude, longitude = point.longitude)
                mapService.reverseGeocode(point) callback@ { result ->
                    if (requestRevision != selectionRevision) return@callback
                    loading = false
                    result.onSuccess { destination ->
                        selected = destination
                    }.onFailure {
                        selected = Destination(name = "地图选点", latitude = point.latitude, longitude = point.longitude)
                        error = it.message
                    }
                }
            }

            override fun onMapPoiClick(poi: com.baidu.mapapi.map.MapPoi?) = Unit
        })
        mapView.map.setOnMarkerClickListener(object : BaiduMap.OnMarkerClickListener {
            override fun onMarkerClick(clickedMarker: Marker): Boolean {
                if (clickedMarker != marker) return false
                clearDestinationSelection()
                return true
            }
        })
        selected?.let { showDestination(it, mapZoomLevel) }
    }

    LaunchedEffect(isOnline) {
        if (isOnline) {
            mapView.map.mapRefresh()
        } else {
            searchResults = emptyList()
            loading = false
        }
    }

    LaunchedEffect(locationState.timestampMillis, locationState.isValid, followMyLocation, headingUp, selected?.id) {
        if (!locationState.isValid) return@LaunchedEffect
        val latitude = locationState.latitude ?: return@LaunchedEffect
        val longitude = locationState.longitude ?: return@LaunchedEffect
        val point = LatLng(latitude, longitude)
        val cameraRotation = when {
            followMyLocation && headingUp -> navigationHeadingStep
            followMyLocation -> 0f
            else -> mapView.map.mapStatus.rotate
        }
        val markerRotation = (360f - navigationHeading + cameraRotation) % 360f
        val animationDuration = (
            locationState.updateIntervalMillis.coerceAtLeast(1_000L) * 9 / 10
            ).coerceAtMost(1_200L)
        val existing = currentLocationMarker
        if (existing == null) {
            currentLocationMarker = mapView.map.addOverlay(
                MarkerOptions()
                    .position(point)
                    .icon(createHeadingMarker(context))
                    .anchor(.5f, .5f)
                    .rotate(markerRotation)
                    .zIndex(3)
            ) as Marker
        }
        val destinationPoint = selected?.let { LatLng(it.latitude, it.longitude) }
        val line = currentDestinationLine
        if (destinationPoint == null) {
            line?.remove()
            currentDestinationLine = null
        } else if (line == null) {
            currentDestinationLine = mapView.map.addOverlay(
                PolylineOptions()
                    .points(listOf(point, destinationPoint))
                    .width(maxOf(2, (.6f * context.resources.displayMetrics.density).roundToInt()))
                    .color(destinationLineColor)
                    .zIndex(2)
            ) as Polyline
        } else {
            line.points = listOf(point, destinationPoint)
        }
        val accuracy = (locationState.accuracyMeters ?: 100f).toDouble().coerceAtLeast(5.0)
        val circle = currentAccuracyCircle
        if (circle == null) {
            currentAccuracyCircle = mapView.map.addOverlay(
                CircleOptions()
                    .center(point)
                    .radius(accuracy.roundToInt())
                    .stroke(Stroke(2, Color(0xFF4285F4).copy(alpha = .72f).toArgb()))
                    .fillColor(Color(0xFF4285F4).copy(alpha = .14f).toArgb())
                    .zIndex(1)
            ) as Circle
        } else {
            circle.setRadius(accuracy.roundToInt())
        }
        if (existing != null) {
            markerAnimator.animatePosition(
                marker = existing,
                accuracyCircle = currentAccuracyCircle,
                destinationLine = currentDestinationLine,
                destination = destinationPoint,
                target = point,
                durationMillis = animationDuration
            )
            markerAnimator.animateRotation(existing, markerRotation)
        }
        if (followMyLocation) {
            val status = MapStatus.Builder(mapView.map.mapStatus)
                .target(point)
                .rotate(cameraRotation)
                .build()
            mapView.map.animateMapStatus(MapStatusUpdateFactory.newMapStatus(status), animationDuration.toInt())
        }
    }

    LaunchedEffect(navigationHeadingStep, useMovementHeading, followMyLocation, headingUp) {
        val marker = currentLocationMarker ?: return@LaunchedEffect
        val cameraRotation = when {
            followMyLocation && headingUp -> navigationHeadingStep
            followMyLocation -> 0f
            else -> mapView.map.mapStatus.rotate
        }
        markerAnimator.animateRotation(marker, (360f - navigationHeading + cameraRotation) % 360f)
        if (followMyLocation && headingUp && !useMovementHeading) {
            val status = MapStatus.Builder(mapView.map.mapStatus)
                .target(marker.position)
                .rotate(navigationHeadingStep)
                .build()
            mapView.map.animateMapStatus(MapStatusUpdateFactory.newMapStatus(status), 280)
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 40.dp,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = Color.Transparent,
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        sheetTonalElevation = 0.dp,
        sheetShadowElevation = 12.dp,
        sheetDragHandle = null,
        sheetContent = {
            Box(Modifier.fillMaxWidth()) {
                FrostedMapLayer(
                    blurTarget = mapBlurTarget,
                    overlayColor = blurOverlayColor,
                    cornerRadius = 24.dp,
                    modifier = Modifier.matchParentSize()
                )
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(40.dp)) {
                        BottomSheetDefaults.DragHandle(Modifier.align(Alignment.Center))
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                    ) {
                        val displayedDestination = selected
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("目标地点", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(displayedDestination?.name ?: "无目标", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            }
                            if (loading) CircularProgressIndicator(Modifier.width(22.dp), strokeWidth = 2.dp)
                        }
                        if (displayedDestination == null) {
                            Text(
                                "当前未选择目标地点，罗盘将作为普通指南针使用。",
                                Modifier.padding(top = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            if (displayedDestination.address.isNotBlank()) {
                                Text(displayedDestination.address, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Coordinate("纬度", displayedDestination.latitude, Modifier.weight(1f))
                                Coordinate("经度", displayedDestination.longitude, Modifier.weight(1f))
                            }
                            error?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            OutlinedButton(
                                onClick = { onFavoriteToggle(displayedDestination, selectedIsFavorite) },
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp)
                            ) {
                                Icon(
                                    if (selectedIsFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (selectedIsFavorite) "取消收藏" else "收藏地点")
                            }
                            Button(onClick = { onConfirm(displayedDestination) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp)) {
                                Text("确认目标")
                            }
                        }
                    }
                }
            }
        }
    ) { _ ->
        // Keep the map rendered behind the complete sheet. If the scaffold's peek-height
        // padding is applied here, the bottom strip has no map pixels for BlurView to sample.
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { mapBlurTarget },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    .shadow(6.dp, searchShape)
                    .clip(searchShape)
                    .border(1.dp, glassHighlightColor, searchShape)
            ) {
                FrostedMapLayer(
                    blurTarget = mapBlurTarget,
                    overlayColor = blurOverlayColor,
                    cornerRadius = 20.dp,
                    modifier = Modifier.matchParentSize()
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; error = null },
                    singleLine = true,
                    placeholder = { Text("搜索地点") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        when {
                            loading -> CircularProgressIndicator(Modifier.width(22.dp), strokeWidth = 2.dp)
                            query.isNotBlank() -> Row {
                                IconButton(onClick = { query = ""; searchResults = emptyList() }) { Icon(Icons.Filled.Close, "清除") }
                                IconButton(onClick = { performSearch() }) { Icon(Icons.Filled.Search, "搜索") }
                            }
                            else -> Unit
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                    shape = searchShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AnimatedVisibility(
                visible = !isOnline,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 84.dp)
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        "当前离线，地图搜索暂不可用",
                        Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            AnimatedVisibility(
                visible = destinationProximity != DestinationProximity.FAR &&
                    searchResults.isEmpty() && isOnline,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 16.dp, top = 84.dp, end = 16.dp)
            ) {
                DestinationProximityNotice(
                    proximity = destinationProximity,
                    distanceMeters = destinationDistanceMeters
                )
            }
            if (searchResults.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 84.dp)
                        .heightIn(max = 320.dp)
                ) {
                    LazyColumn {
                        items(searchResults, key = { it.id }) { result ->
                            ListItem(
                                headlineContent = { Text(result.name, fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Text(
                                        result.address.ifBlank { String.format(Locale.US, "%.4f, %.4f", result.latitude, result.longitude) },
                                        maxLines = 2
                                    )
                                },
                                leadingContent = { Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    query = result.name
                                    searchResults = emptyList()
                                    error = null
                                    onFollowMyLocationChange(false)
                                    showDestination(result)
                                    scope.launch { bottomSheetState.expand() }
                                }
                            )
                            HorizontalDivider(Modifier.padding(start = 56.dp))
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 56.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (locationState.hasFix) {
                    val accuracy = locationState.accuracyMeters ?: Float.MAX_VALUE
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (accuracy <= GPS_WEAK_SIGNAL_THRESHOLD_METERS && hasPreciseLocation) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            if (hasPreciseLocation) "定位精度 ±${accuracy.roundToInt()} m" else "当前为模糊定位",
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (accuracy <= GPS_WEAK_SIGNAL_THRESHOLD_METERS && hasPreciseLocation) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FrostedMapFab(
                    blurTarget = mapBlurTarget,
                    overlayColor = blurOverlayColor,
                    highlightColor = glassHighlightColor,
                    onClick = {
                        if (!followMyLocation) onFollowMyLocationChange(true)
                        onHeadingUpChange(!headingUp)
                    },
                    containerColor = if (headingUp) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (headingUp) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Filled.Navigation,
                        if (headingUp) "关闭车头朝上" else "开启车头朝上"
                    )
                }
                Spacer(Modifier.height(8.dp))
                FrostedMapFab(
                    blurTarget = mapBlurTarget,
                    overlayColor = blurOverlayColor,
                    highlightColor = glassHighlightColor,
                    onClick = {
                        when {
                            !hasLocationPermission || !hasPreciseLocation -> {
                                error = if (hasLocationPermission) "请在授权弹窗中开启精确位置" else null
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            }
                            !locationState.isLocationEnabled -> {
                                error = "系统定位已关闭"
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }
                            locationState.latitude != null && locationState.longitude != null -> {
                                error = null
                                val point = LatLng(locationState.latitude, locationState.longitude)
                                val enableFollow = !followMyLocation
                                onFollowMyLocationChange(enableFollow)
                                if (enableFollow) {
                                    val status = MapStatus.Builder(mapView.map.mapStatus)
                                        .target(point)
                                        .zoom(17f)
                                        .rotate(if (headingUp) navigationHeadingStep else 0f)
                                        .build()
                                    mapView.map.animateMapStatus(MapStatusUpdateFactory.newMapStatus(status), 500)
                                }
                            }
                            else -> error = "正在获取当前位置，请确认系统定位已开启"
                        }
                    },
                    containerColor = if (followMyLocation) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (followMyLocation) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (followMyLocation) Icons.Filled.GpsFixed else Icons.Filled.MyLocation,
                        if (followMyLocation) "停止跟随我的位置" else "回到我的位置"
                    )
                }
            }
        }
    }
}

@Composable
private fun FrostedMapFab(
    blurTarget: BlurTarget,
    overlayColor: Color,
    highlightColor: Color,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .border(1.dp, highlightColor, shape)
    ) {
        FrostedMapLayer(
            blurTarget = blurTarget,
            overlayColor = overlayColor,
            cornerRadius = 16.dp,
            modifier = Modifier.matchParentSize()
        )
        SmallFloatingActionButton(
            onClick = onClick,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            content = content
        )
    }
}

@Composable
private fun FrostedMapLayer(
    blurTarget: BlurTarget,
    overlayColor: Color,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    AndroidView(
        factory = { context ->
            BlurView(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    this.cornerRadius = cornerRadiusPx
                    setColor(android.graphics.Color.TRANSPARENT)
                }
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                setupWith(blurTarget)
                    .setBlurRadius(20f)
                    .setOverlayColor(overlayColor.toArgb())
                    .setBlurAutoUpdate(true)
            }
        },
        update = { blurView ->
            (blurView.background as? GradientDrawable)?.cornerRadius = cornerRadiusPx
            blurView.setBlurRadius(20f)
            blurView.setOverlayColor(overlayColor.toArgb())
        },
        modifier = modifier
    )
}

internal enum class DestinationProximity { FAR, NEAR, ARRIVED }

internal fun destinationProximityForDistance(distanceMeters: Double?): DestinationProximity = when {
    distanceMeters == null -> DestinationProximity.FAR
    distanceMeters <= 10.0 -> DestinationProximity.ARRIVED
    distanceMeters <= 50.0 -> DestinationProximity.NEAR
    else -> DestinationProximity.FAR
}

internal fun shouldUseMovementHeading(locationState: LocationState): Boolean =
    locationState.bearingDegrees != null &&
        locationState.motionState != MotionState.STATIONARY &&
        locationState.speedMetersPerSecond >= MIN_GPS_HEADING_SPEED_METERS_PER_SECOND

@Composable
internal fun DestinationProximityNotice(
    proximity: DestinationProximity,
    distanceMeters: Double?
) {
    val arrived = proximity == DestinationProximity.ARRIVED
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (arrived) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (arrived) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                if (arrived) Icons.Filled.Flag else Icons.Filled.NearMe,
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (arrived) "目的地已到达" else "接近目的地",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                distanceMeters?.let {
                    Text(
                        if (arrived) "已进入目的地 10 米范围" else "距离约 ${it.roundToInt()} 米",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun createHeadingMarker(context: Context): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val size = (36f * density).roundToInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val center = size / 2f

    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(52, 66, 133, 244)
    }
    canvas.drawCircle(center, center, size * .48f, halo)

    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(66, 133, 244)
        style = Paint.Style.FILL
        setShadowLayer(size * .06f, 0f, size * .025f, android.graphics.Color.argb(90, 0, 0, 0))
    }
    canvas.setDrawFilter(android.graphics.PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    val arrow = android.graphics.Path().apply {
        moveTo(center, size * .08f)
        lineTo(size * .78f, size * .76f)
        lineTo(center, size * .62f)
        lineTo(size * .22f, size * .76f)
        close()
    }
    canvas.drawPath(arrow, body)
    canvas.drawCircle(center, size * .56f, size * .08f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private const val MIN_GPS_HEADING_SPEED_METERS_PER_SECOND = 1f

private class MapMarkerAnimator {
    private var positionAnimator: ValueAnimator? = null
    private var rotationAnimator: ValueAnimator? = null

    fun animatePosition(
        marker: Marker,
        accuracyCircle: Circle?,
        destinationLine: Polyline?,
        destination: LatLng?,
        target: LatLng,
        durationMillis: Long
    ) {
        val start = marker.position
        if (start.latitude == target.latitude && start.longitude == target.longitude) return
        positionAnimator?.cancel()
        positionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMillis
            interpolator = DecelerateInterpolator(1.15f)
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val position = LatLng(
                    start.latitude + (target.latitude - start.latitude) * fraction,
                    start.longitude + (target.longitude - start.longitude) * fraction
                )
                marker.position = position
                accuracyCircle?.center = position
                if (destination != null) destinationLine?.points = listOf(position, destination)
            }
            start()
        }
    }

    fun animateRotation(marker: Marker, targetDegrees: Float) {
        val start = marker.rotate
        val target = (targetDegrees % 360f + 360f) % 360f
        val delta = ((target - start + 540f) % 360f) - 180f
        if (kotlin.math.abs(delta) < 0.5f) return
        rotationAnimator?.cancel()
        rotationAnimator = ValueAnimator.ofFloat(start, start + delta).apply {
            duration = 320L
            interpolator = DecelerateInterpolator()
            addUpdateListener { marker.rotate = it.animatedValue as Float }
            start()
        }
    }

    fun cancel() {
        positionAnimator?.cancel()
        rotationAnimator?.cancel()
    }
}

private fun createDestinationMarker(context: Context): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val width = (36f * density).roundToInt()
    val height = (48f * density).roundToInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(234, 67, 53)
        style = Paint.Style.FILL
        setShadowLayer(width * .08f, 0f, width * .04f, android.graphics.Color.argb(80, 0, 0, 0))
    }
    val centerX = width / 2f
    val radius = width * .36f
    canvas.drawCircle(centerX, radius + width * .08f, radius, paint)
    val tip = android.graphics.Path().apply {
        moveTo(centerX - radius * .7f, radius * 1.25f)
        lineTo(centerX + radius * .7f, radius * 1.25f)
        lineTo(centerX, height * .94f)
        close()
    }
    canvas.drawPath(tip, paint)
    canvas.drawCircle(
        centerX,
        radius + width * .08f,
        radius * .38f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    )
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

@Composable
private fun Coordinate(label: String, value: Double, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(String.format(Locale.US, "%.4f", value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}
