package com.destinationcompass.app.presentation

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Point
import android.os.SystemClock
import android.provider.Settings
import android.view.animation.DecelerateInterpolator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.baidu.mapapi.model.LatLngBounds
import com.destinationcompass.app.data.map.MapService
import com.destinationcompass.app.data.map.BaiduMapSdkInitializer
import com.destinationcompass.app.data.map.PlannedRoute
import com.destinationcompass.app.data.map.RoutePlanningService
import com.destinationcompass.app.data.map.RoutePreference
import com.destinationcompass.app.data.map.RouteTravelMode
import com.destinationcompass.app.data.location.LocationState
import com.destinationcompass.app.data.location.MotionState
import com.destinationcompass.app.data.location.GPS_WEAK_SIGNAL_THRESHOLD_METERS
import com.destinationcompass.app.domain.BearingCalculator
import com.destinationcompass.app.model.Destination
import com.destinationcompass.app.ui.liquidglass.GlassBottomSheet
import com.destinationcompass.app.ui.liquidglass.GlassButton
import com.destinationcompass.app.ui.liquidglass.GlassCard
import com.destinationcompass.app.ui.liquidglass.GlassFloatingActionButton
import com.destinationcompass.app.ui.liquidglass.GlassQuality
import com.destinationcompass.app.ui.liquidglass.GlassSurface
import com.destinationcompass.app.ui.liquidglass.GlassToggle
import com.destinationcompass.app.ui.liquidglass.WindowAlignedLayerBackdrop
import com.kyant.backdrop.Backdrop
import com.destinationcompass.app.ui.liquidglass.GlassTokens
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    backdrop: LayerBackdrop,
    detailSheetBackdrop: WindowAlignedLayerBackdrop,
    initialDestination: Destination?,
    hasLocationPermission: Boolean,
    hasPreciseLocation: Boolean,
    locationState: LocationState,
    userHeading: Float,
    isOnline: Boolean,
    followMyLocation: Boolean,
    headingUp: Boolean,
    mapZoomLevel: Float,
    initialPlannedRoute: PlannedRoute?,
    navigationActive: Boolean,
    favoriteIds: Set<String>,
    onLocationPermissionResult: (Boolean) -> Unit,
    onFollowMyLocationChange: (Boolean) -> Unit,
    onHeadingUpChange: (Boolean) -> Unit,
    onMapZoomLevelChange: (Float) -> Unit,
    onRoutePlanned: (Destination, PlannedRoute) -> Unit,
    onRouteCleared: () -> Unit,
    onNavigationActiveChange: (Boolean) -> Unit,
    onFavoriteToggle: (Destination, Boolean) -> Unit,
    onClearDestination: () -> Unit,
    onConfirm: (Destination) -> Unit
) {
    val context = LocalContext.current
    BaiduMapSdkInitializer.ensureInitialized(context)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val searchShape = remember { RoundedCornerShape(20.dp) }
    val destinationLineColor = Color(0xFF0B57D0).toArgb()
    val plannedRouteColor = MaterialTheme.colorScheme.primary.toArgb()
    val mapView = remember { TextureMapView(context) }
    val mapService = remember { MapService() }
    val routePlanningService = remember { RoutePlanningService() }
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
    var plannedRouteLine by remember { mutableStateOf<Polyline?>(null) }
    var plannedRoute by remember(initialPlannedRoute) { mutableStateOf(initialPlannedRoute) }
    var routeTravelMode by remember(initialPlannedRoute?.travelMode) {
        mutableStateOf(initialPlannedRoute?.travelMode ?: RouteTravelMode.WALKING)
    }
    var routePreference by remember(initialPlannedRoute?.preference) {
        mutableStateOf(initialPlannedRoute?.preference ?: RoutePreference.SHORTEST)
    }
    var routeLoading by remember { mutableStateOf(false) }
    var routeRequestRevision by remember { mutableIntStateOf(0) }
    var startNavigationAfterPlanning by remember { mutableStateOf(false) }
    var lastNavigationRouteOrigin by remember { mutableStateOf<LatLng?>(null) }
    var lastNavigationRouteRequestMillis by remember { mutableStateOf(0L) }
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

    fun clearPlannedRoute(notify: Boolean = true) {
        routeRequestRevision += 1
        routePlanningService.cancel()
        routeLoading = false
        startNavigationAfterPlanning = false
        plannedRouteLine?.remove()
        plannedRouteLine = null
        plannedRoute = null
        lastNavigationRouteOrigin = null
        lastNavigationRouteRequestMillis = 0L
        if (notify) onRouteCleared()
    }

    fun showDestination(destination: Destination, zoom: Float = 17f, clearRoute: Boolean = true) {
        if (clearRoute) clearPlannedRoute()
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
        clearPlannedRoute()
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

    fun renderPlannedRoute(route: PlannedRoute, fitRoute: Boolean) {
        plannedRouteLine?.remove()
        plannedRoute = route
        plannedRouteLine = mapView.map.addOverlay(
            PolylineOptions()
                .points(route.points)
                .width(maxOf(1, (1.5f * context.resources.displayMetrics.density).roundToInt()))
                .color(plannedRouteColor)
                .lineJoinType(PolylineOptions.LineJoinType.LineJoinRound)
                .lineCapType(PolylineOptions.LineCapType.LineCapRound)
                .zIndex(2)
        ) as Polyline
        if (fitRoute) {
            val bounds = LatLngBounds.Builder().include(route.points).build()
            mapView.map.animateMapStatus(
                MapStatusUpdateFactory.newLatLngZoom(bounds, 64, 120, 64, 160)
            )
        }
    }

    fun performRoutePlanning(navigationUpdate: Boolean = false) {
        val destination = selected ?: run {
            error = "请先选择目标地点"
            return
        }
        if (!isOnline) {
            error = "网络不可用，连接网络后才能规划路线"
            return
        }
        if (!locationState.isValid) {
            error = "正在获取有效当前位置，请稍后再试"
            return
        }
        val latitude = locationState.latitude ?: return
        val longitude = locationState.longitude ?: return
        val origin = LatLng(latitude, longitude)
        val destinationPoint = LatLng(destination.latitude, destination.longitude)
        val requestRevision = routeRequestRevision + 1
        routeRequestRevision = requestRevision
        routeLoading = true
        error = null

        routePlanningService.planRoute(
            origin = origin,
            destination = destinationPoint,
            travelMode = routeTravelMode,
            preference = routePreference
        ) callback@ { result ->
            if (requestRevision != routeRequestRevision) return@callback
            routeLoading = false
            result.onSuccess { route ->
                renderPlannedRoute(route, fitRoute = !navigationUpdate)
                onRoutePlanned(destination, route)
                if (startNavigationAfterPlanning) {
                    startNavigationAfterPlanning = false
                    lastNavigationRouteOrigin = null
                    lastNavigationRouteRequestMillis = 0L
                    onNavigationActiveChange(true)
                }
                if (!navigationUpdate) {
                    scope.launch { bottomSheetState.partialExpand() }
                }
            }.onFailure {
                startNavigationAfterPlanning = false
                error = it.message ?: "路线规划失败"
            }
        }
    }

    fun setRealtimeNavigation(enabled: Boolean) {
        if (!enabled) {
            onNavigationActiveChange(false)
            return
        }
        when {
            !isOnline -> {
                error = "网络不可用，无法启动实时导航"
                scope.launch { bottomSheetState.expand() }
            }
            !locationState.isValid -> {
                error = "正在获取有效当前位置，暂时无法启动实时导航"
                scope.launch { bottomSheetState.expand() }
            }
            selected == null -> {
                error = "请先选择目标地点"
                scope.launch { bottomSheetState.expand() }
            }
            plannedRoute == null -> {
                startNavigationAfterPlanning = true
                performRoutePlanning()
            }
            else -> {
                error = null
                lastNavigationRouteOrigin = null
                lastNavigationRouteRequestMillis = 0L
                onNavigationActiveChange(true)
                scope.launch { bottomSheetState.partialExpand() }
            }
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
            routePlanningService.destroy()
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
                clearPlannedRoute()
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
        selected?.let { showDestination(it, mapZoomLevel, clearRoute = false) }
        plannedRoute?.let { renderPlannedRoute(it, fitRoute = false) }
    }

    LaunchedEffect(isOnline) {
        if (isOnline) {
            mapView.map.mapRefresh()
        } else {
            searchResults = emptyList()
            loading = false
        }
    }

    LaunchedEffect(
        locationState.timestampMillis,
        locationState.isValid,
        followMyLocation,
        headingUp,
        selected?.id,
        plannedRoute
    ) {
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
                    .zIndex(4)
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

    LaunchedEffect(
        navigationActive,
        locationState.timestampMillis,
        selected?.id,
        isOnline,
        routeLoading
    ) {
        if (!navigationActive || !isOnline || routeLoading || !locationState.isValid || selected == null) {
            return@LaunchedEffect
        }
        val latitude = locationState.latitude ?: return@LaunchedEffect
        val longitude = locationState.longitude ?: return@LaunchedEffect
        val currentOrigin = LatLng(latitude, longitude)
        val previousOrigin = lastNavigationRouteOrigin
        val movedMeters = previousOrigin?.let {
            BearingCalculator.distanceMeters(
                it.latitude,
                it.longitude,
                currentOrigin.latitude,
                currentOrigin.longitude
            )
        } ?: Double.POSITIVE_INFINITY
        val now = SystemClock.elapsedRealtime()
        val elapsedMillis = now - lastNavigationRouteRequestMillis
        val shouldReplan = previousOrigin == null ||
            (movedMeters >= NAVIGATION_REPLAN_DISTANCE_METERS && elapsedMillis >= NAVIGATION_REPLAN_MIN_INTERVAL_MILLIS)
        if (shouldReplan) {
            lastNavigationRouteOrigin = currentOrigin
            lastNavigationRouteRequestMillis = now
            performRoutePlanning(navigationUpdate = true)
        }
    }

    LaunchedEffect(
        navigationActive,
        locationState.timestampMillis,
        locationState.motionState,
        plannedRoute
    ) {
        val route = plannedRoute ?: return@LaunchedEffect
        if (navigationActive || !locationState.isValid || locationState.motionState == MotionState.STATIONARY) {
            return@LaunchedEffect
        }
        val latitude = locationState.latitude ?: return@LaunchedEffect
        val longitude = locationState.longitude ?: return@LaunchedEffect
        val routeOrigin = route.points.firstOrNull() ?: return@LaunchedEffect
        val distanceFromRouteOrigin = BearingCalculator.distanceMeters(
            routeOrigin.latitude,
            routeOrigin.longitude,
            latitude,
            longitude
        )
        if (distanceFromRouteOrigin >= STATIC_ROUTE_CANCEL_DISTANCE_METERS) {
            clearPlannedRoute()
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
        // The scaffold deliberately fills the map so the floating navigation bar
        // can refract it. Include the bar's footprint in the peek anchor instead
        // of letting the sheet handle and details open underneath the bar.
        sheetPeekHeight = 40.dp + GlassTokens.NavigationContentClearance,
        sheetShape = GlassTokens.BottomSheetShape,
        sheetContainerColor = Color.Transparent,
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        sheetTonalElevation = 0.dp,
        sheetShadowElevation = 0.dp,
        sheetDragHandle = null,
        sheetContent = {
            GlassBottomSheet(
                backdrop = backdrop,
                modifier = detailSheetBackdrop.sourceModifier,
                exportedBackdrop = detailSheetBackdrop.layerBackdrop
            ) { sheetContentBackdrop ->
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(40.dp)) {
                        BottomSheetDefaults.DragHandle(Modifier.align(Alignment.Center))
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = 24.dp,
                                end = 24.dp,
                                bottom = 24.dp + GlassTokens.NavigationContentClearance
                            )
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
                            Text(
                                "路线规划",
                                Modifier.padding(top = 16.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RouteChoiceChip(
                                        text = "步行",
                                        selected = routeTravelMode == RouteTravelMode.WALKING,
                                        backdrop = sheetContentBackdrop,
                                        onClick = {
                                            if (routeTravelMode != RouteTravelMode.WALKING) {
                                                routeTravelMode = RouteTravelMode.WALKING
                                                clearPlannedRoute()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    RouteChoiceChip(
                                        text = "骑行",
                                        selected = routeTravelMode == RouteTravelMode.CYCLING,
                                        backdrop = sheetContentBackdrop,
                                        onClick = {
                                            if (routeTravelMode != RouteTravelMode.CYCLING) {
                                                routeTravelMode = RouteTravelMode.CYCLING
                                                clearPlannedRoute()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RouteChoiceChip(
                                        text = "距离最短",
                                        selected = routePreference == RoutePreference.SHORTEST,
                                        backdrop = sheetContentBackdrop,
                                        onClick = {
                                            if (routePreference != RoutePreference.SHORTEST) {
                                                routePreference = RoutePreference.SHORTEST
                                                clearPlannedRoute()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    RouteChoiceChip(
                                        text = "时间最快",
                                        selected = routePreference == RoutePreference.FASTEST,
                                        backdrop = sheetContentBackdrop,
                                        onClick = {
                                            if (routePreference != RoutePreference.FASTEST) {
                                                routePreference = RoutePreference.FASTEST
                                                clearPlannedRoute()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            plannedRoute?.let { route ->
                                GlassSurface(
                                    backdrop = sheetContentBackdrop,
                                    shape = RoundedCornerShape(18.dp),
                                    quality = GlassQuality.High,
                                    blurRadius = GlassTokens.StrongBlurRadius,
                                    surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f),
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                ) {
                                    Text(
                                        "${route.travelMode.displayName} · ${formatRouteDistance(route.distanceMeters)} · " +
                                            "${formatRouteDuration(route.durationSeconds)} · ${route.preference.displayName}",
                                        Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("实时导航", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (plannedRoute == null) "开启后自动规划路线" else "移动时自动重新规划路线",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                GlassToggle(
                                    checked = navigationActive,
                                    enabled = !routeLoading,
                                    onCheckedChange = ::setRealtimeNavigation,
                                    backdrop = sheetContentBackdrop
                                )
                            }
                            GlassButton(
                                onClick = { performRoutePlanning() },
                                backdrop = sheetContentBackdrop,
                                enabled = !routeLoading,
                                loading = routeLoading,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp)
                            ) {
                                Text(if (routeLoading) "正在规划…" else "规划${routeTravelMode.displayName}路线")
                            }
                            error?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            GlassButton(
                                onClick = { onFavoriteToggle(displayedDestination, selectedIsFavorite) },
                                backdrop = sheetContentBackdrop,
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp)
                            ) {
                                Icon(
                                    if (selectedIsFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (selectedIsFavorite) "取消收藏" else "收藏地点")
                            }
                            GlassButton(
                                onClick = { onConfirm(displayedDestination) },
                                backdrop = sheetContentBackdrop,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp)
                            ) {
                                Text("确认目标")
                            }
                        }
                    }
                }
            }
        }
    ) { _ ->
        // Only the map is exported to the page backdrop. Glass overlays are siblings, so
        // no node records and samples the same LayerBackdrop during one draw pass.
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)
            )
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                shape = searchShape,
                blurRadius = GlassTokens.StrongBlurRadius,
                quality = GlassQuality.High
            ) {
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
                GlassSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(16.dp),
                    quality = GlassQuality.High,
                    blurRadius = GlassTokens.StrongBlurRadius,
                    surfaceColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                ) {
                    Text(
                        "当前离线，地图搜索暂不可用",
                        Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
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
                    distanceMeters = destinationDistanceMeters,
                    backdrop = backdrop
                )
            }
            if (searchResults.isNotEmpty()) {
                GlassCard(
                    backdrop = backdrop,
                    shape = remember { RoundedCornerShape(18.dp) },
                    quality = GlassQuality.Balanced,
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
            AnimatedVisibility(
                visible = selected != null && searchResults.isEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 96.dp, bottom = 140.dp)
            ) {
                val route = plannedRoute
                val routeCardBackdrop = rememberLayerBackdrop()
                val routeCardShape = remember { RoundedCornerShape(18.dp) }
                GlassCard(
                    backdrop = backdrop,
                    exportedBackdrop = routeCardBackdrop,
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = routeCardShape,
                    quality = GlassQuality.High
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    navigationActive -> "路线导航中"
                                    route != null -> "路线已规划"
                                    else -> "尚未规划路线"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (route != null) {
                                    "${route.travelMode.displayName} · ${formatRouteDistance(route.distanceMeters)} · " +
                                        formatRouteDuration(route.durationSeconds)
                                } else {
                                    "点击实时导航自动规划路线"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                        GlassButton(
                            onClick = { setRealtimeNavigation(!navigationActive) },
                            backdrop = routeCardBackdrop,
                            enabled = !routeLoading,
                            loading = routeLoading,
                            surfaceColor = if (navigationActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                            } else {
                                Color.Unspecified
                            },
                            contentColor = if (navigationActive) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text(
                                when {
                                    routeLoading -> "规划中…"
                                    navigationActive -> "结束导航"
                                    else -> "实时导航"
                                }
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 140.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (locationState.hasFix) {
                    val accuracy = locationState.accuracyMeters ?: Float.MAX_VALUE
                    val accurate = accuracy <= GPS_WEAK_SIGNAL_THRESHOLD_METERS && hasPreciseLocation
                    GlassSurface(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(12.dp),
                        quality = GlassQuality.High,
                        surfaceColor = if (accurate) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                        }
                    ) {
                        Text(
                            if (hasPreciseLocation) "定位精度 ±${accuracy.roundToInt()} m" else "当前为模糊定位",
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (accurate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                GlassFloatingActionButton(
                    backdrop = backdrop,
                    onClick = {
                        if (!followMyLocation) onFollowMyLocationChange(true)
                        onHeadingUpChange(!headingUp)
                    },
                    surfaceColor = if (headingUp) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
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
                GlassFloatingActionButton(
                    backdrop = backdrop,
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
                    surfaceColor = if (followMyLocation) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
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
    distanceMeters: Double?,
    backdrop: Backdrop
) {
    val arrived = proximity == DestinationProximity.ARRIVED
    GlassCard(
        backdrop = backdrop,
        shape = RoundedCornerShape(16.dp),
        quality = GlassQuality.High,
        blurRadius = GlassTokens.StrongBlurRadius,
        surfaceColor = if (arrived) {
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f)
        },
        interactive = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        val contentColor = if (arrived) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                if (arrived) Icons.Filled.Flag else Icons.Filled.NearMe,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (arrived) "目的地已到达" else "接近目的地",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                distanceMeters?.let {
                    Text(
                        if (arrived) "已进入目的地 10 米范围" else "距离约 ${it.roundToInt()} 米",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
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
private const val NAVIGATION_REPLAN_DISTANCE_METERS = 5.0
private const val NAVIGATION_REPLAN_MIN_INTERVAL_MILLIS = 3_000L
private const val STATIC_ROUTE_CANCEL_DISTANCE_METERS = 5.0

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
private fun RouteChoiceChip(
    text: String,
    selected: Boolean,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassButton(
        onClick = onClick,
        backdrop = backdrop,
        surfaceColor = if (selected) {
            // Use the saturated accent rather than the pale container color so
            // the selected route option remains obvious over a busy map backdrop.
            MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = modifier.height(42.dp)
    ) {
        Text(text)
    }
}

private fun formatRouteDistance(distanceMeters: Int): String = if (distanceMeters < 1_000) {
    "$distanceMeters m"
} else {
    String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1_000f)
}

private fun formatRouteDuration(durationSeconds: Int): String {
    val minutes = (durationSeconds / 60f).roundToInt().coerceAtLeast(1)
    return if (minutes < 60) "$minutes 分钟" else "${minutes / 60} 小时 ${minutes % 60} 分钟"
}

@Composable
private fun Coordinate(label: String, value: Double, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(String.format(Locale.US, "%.4f", value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}
