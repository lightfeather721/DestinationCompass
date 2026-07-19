package com.destinationcompass.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.destinationcompass.app.ui.liquidglass.GlassNavigationItem
import com.destinationcompass.app.ui.liquidglass.GlassNavigationBar
import com.destinationcompass.app.ui.liquidglass.GlassSnackbarHost
import com.destinationcompass.app.ui.liquidglass.rememberWindowAlignedLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

private enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    PLACES("地点", Icons.Filled.Map, Icons.Outlined.Map),
    COMPASS("罗盘", Icons.Filled.Explore, Icons.Outlined.Explore),
    FAVORITES("收藏", Icons.Filled.Star, Icons.Outlined.StarOutline),
    SETTINGS("设置", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun DestinationCompassApp(viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(AppTab.COMPASS) }
    val destination by viewModel.destination.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val theme by viewModel.themeMode.collectAsState()
    val unit by viewModel.distanceUnit.collectAsState()
    val locationRefreshIntervalMillis by viewModel.locationRefreshIntervalMillis.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val mapFollowMyLocation by viewModel.mapFollowMyLocation.collectAsState()
    val mapHeadingUp by viewModel.mapHeadingUp.collectAsState()
    val mapZoomLevel by viewModel.mapZoomLevel.collectAsState()
    val plannedRoute by viewModel.plannedRoute.collectAsState()
    val mapNavigationActive by viewModel.mapNavigationActive.collectAsState()
    val context = LocalContext.current
    val favoriteIds = remember(favorites) { favorites.mapTo(mutableSetOf()) { it.id } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasPreciseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val backgroundColor = MaterialTheme.colorScheme.background
    // AnimatedContent keeps the outgoing and incoming pages attached together.
    // Each page needs its own source: otherwise the outgoing page can detach last
    // and clear the shared LayerBackdrop coordinates used by the new page's tabs.
    val placesBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val compassBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val favoritesBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val settingsBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val activePageBackdrop = when (tab) {
        AppTab.PLACES -> placesBackdrop
        AppTab.COMPASS -> compassBackdrop
        AppTab.FAVORITES -> favoritesBackdrop
        AppTab.SETTINGS -> settingsBackdrop
    }
    val mapDetailSheetBackdrop = rememberWindowAlignedLayerBackdrop()
    val fullScreenCardBackdrop = rememberWindowAlignedLayerBackdrop()
    val navigationBackdrop = if (tab == AppTab.PLACES) {
        rememberCombinedBackdrop(placesBackdrop, mapDetailSheetBackdrop)
    } else {
        activePageBackdrop
    }
    val navigationItems = remember {
        AppTab.entries.map { item ->
            GlassNavigationItem(
                label = item.label,
                selectedIcon = item.selectedIcon,
                unselectedIcon = item.unselectedIcon
            )
        }
    }

    fun changeTab(target: AppTab) {
        if (tab == AppTab.PLACES && target != AppTab.PLACES) {
            viewModel.clearPlannedRouteIfNavigationInactive()
        }
        tab = target
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .then(fullScreenCardBackdrop.sourceModifier)
            .layerBackdrop(fullScreenCardBackdrop.layerBackdrop)
    ) {
        Scaffold(
            containerColor = backgroundColor,
            // Keep status-bar protection, but let page rendering continue under
            // ColorOS' gesture area so the handle no longer sits on a blank band.
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            ),
            snackbarHost = {
                GlassSnackbarHost(
                    hostState = snackbar,
                    backdrop = navigationBackdrop,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 92.dp)
                )
            }
        ) { contentPadding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = { sharedAxisTransition(initialState.ordinal, targetState.ordinal) },
                label = "Material shared axis tab transition",
                modifier = Modifier.padding(contentPadding)
            ) { currentTab ->
                when (currentTab) {
                AppTab.PLACES -> {
                    // Only the map page observes live heading here. Keeping this collection out
                    // of the app shell prevents every sensor frame from recomposing navigation.
                    val compassMetrics by viewModel.metrics.collectAsState()
                    MapPickerScreen(
                        backdrop = placesBackdrop,
                        detailSheetBackdrop = mapDetailSheetBackdrop,
                        initialDestination = destination,
                        hasLocationPermission = hasLocationPermission,
                        hasPreciseLocation = hasPreciseLocation,
                        locationState = locationState,
                        userHeading = compassMetrics.heading,
                        isOnline = isOnline,
                        followMyLocation = mapFollowMyLocation,
                        headingUp = mapHeadingUp,
                        mapZoomLevel = mapZoomLevel,
                        initialPlannedRoute = plannedRoute,
                        navigationActive = mapNavigationActive,
                        favoriteIds = favoriteIds,
                        onLocationPermissionResult = viewModel::onLocationPermissionResult,
                        onFollowMyLocationChange = viewModel::setMapFollowMyLocation,
                        onHeadingUpChange = viewModel::setMapHeadingUp,
                        onMapZoomLevelChange = viewModel::setMapZoomLevel,
                        onRoutePlanned = viewModel::setPlannedRoute,
                        onRouteCleared = viewModel::clearPlannedRoute,
                        onNavigationActiveChange = viewModel::setMapNavigationActive,
                        onFavoriteToggle = { place, isFavorite ->
                            if (isFavorite) {
                                viewModel.deleteFavorite(place.id)
                                scope.launch { snackbar.showSnackbar("已取消收藏 ${place.name}") }
                            } else {
                                viewModel.addFavorite(place)
                                scope.launch { snackbar.showSnackbar("已收藏 ${place.name}") }
                            }
                        },
                        onClearDestination = {
                            viewModel.clearDestination()
                            scope.launch { snackbar.showSnackbar("已取消目标，切换为指南针模式") }
                        },
                        onConfirm = {
                            viewModel.setDestination(it)
                            changeTab(AppTab.COMPASS)
                            scope.launch { snackbar.showSnackbar("已将 ${it.name} 设为目标") }
                        }
                    )
                }
                AppTab.COMPASS -> CompassScreen(
                    backdrop = compassBackdrop,
                    viewModel = viewModel,
                    onChooseDestination = { changeTab(AppTab.PLACES) },
                    onRemoveDestination = {
                        viewModel.clearDestination()
                        scope.launch { snackbar.showSnackbar("已移除目标，切换为指南针模式") }
                    }
                )
                AppTab.FAVORITES -> FavoritesScreen(
                    backdrop = favoritesBackdrop,
                    dialogBackdrop = fullScreenCardBackdrop,
                    favorites = favorites,
                    currentDestination = destination,
                    onUse = {
                        viewModel.setDestination(it)
                        changeTab(AppTab.COMPASS)
                    },
                    onAdd = {
                        viewModel.addFavorite(it)
                        scope.launch { snackbar.showSnackbar("已收藏 ${it.name}") }
                    },
                    onUpdate = viewModel::updateFavorite,
                    onDelete = viewModel::deleteFavorite
                )
                AppTab.SETTINGS -> SettingsScreen(
                    backdrop = settingsBackdrop,
                    dialogBackdrop = fullScreenCardBackdrop,
                    themeMode = theme,
                    unit = unit,
                    locationRefreshIntervalMillis = locationRefreshIntervalMillis,
                    sensorAvailable = viewModel.sensorAvailable,
                    onThemeChange = viewModel::setTheme,
                    onUnitChange = viewModel::setUnit,
                    onLocationRefreshIntervalChange = viewModel::setLocationRefreshInterval
                )
                }
            }
        }

        GlassNavigationBar(
            backdrop = navigationBackdrop,
            selectedIndex = tab.ordinal,
            items = navigationItems,
            onItemSelected = { index -> changeTab(AppTab.entries[index]) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .height(64.dp)
        )
    }
}

private fun sharedAxisTransition(from: Int, to: Int): ContentTransform {
    val direction = if (to > from) 1 else -1
    return (slideInHorizontally(tween(260)) { direction * it / 4 } + fadeIn(tween(180, delayMillis = 50)))
        .togetherWith(slideOutHorizontally(tween(220)) { -direction * it / 5 } + fadeOut(tween(140)))
}
