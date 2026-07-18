package com.destinationcompass.app.data.map

import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.RouteLine
import com.baidu.mapapi.search.core.RouteStep
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.route.BikingRoutePlanOption
import com.baidu.mapapi.search.route.BikingRouteResult
import com.baidu.mapapi.search.route.DrivingRouteResult
import com.baidu.mapapi.search.route.IndoorRouteResult
import com.baidu.mapapi.search.route.IntegralRouteResult
import com.baidu.mapapi.search.route.MassTransitRouteResult
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener
import com.baidu.mapapi.search.route.PlanNode
import com.baidu.mapapi.search.route.RoutePlanSearch
import com.baidu.mapapi.search.route.TransitRouteResult
import com.baidu.mapapi.search.route.WalkingRoutePlanOption
import com.baidu.mapapi.search.route.WalkingRouteResult

enum class RouteTravelMode(val displayName: String) {
    WALKING("步行"),
    CYCLING("骑行")
}

enum class RoutePreference(val displayName: String) {
    SHORTEST("距离最短"),
    FASTEST("时间最快")
}

data class PlannedRoute(
    val points: List<LatLng>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val travelMode: RouteTravelMode,
    val preference: RoutePreference
)

/**
 * Thin wrapper around Baidu's route search. The SDK does not expose a walking/biking
 * policy switch, so the requested preference is applied to the returned alternatives.
 */
class RoutePlanningService {
    private var routeSearch: RoutePlanSearch? = null
    private var requestGeneration = 0
    private var callback: ((Result<PlannedRoute>) -> Unit)? = null
    private var pendingMode = RouteTravelMode.WALKING
    private var pendingPreference = RoutePreference.SHORTEST
    private var pendingOrigin: LatLng? = null
    private var pendingDestination: LatLng? = null

    fun planRoute(
        origin: LatLng,
        destination: LatLng,
        travelMode: RouteTravelMode,
        preference: RoutePreference,
        callback: (Result<PlannedRoute>) -> Unit
    ) {
        this.callback = callback
        pendingMode = travelMode
        pendingPreference = preference
        pendingOrigin = origin
        pendingDestination = destination

        val generation = ++requestGeneration
        routeSearch?.destroy()
        val search = RoutePlanSearch.newInstance()
        routeSearch = search
        search.setOnGetRoutePlanResultListener(object : OnGetRoutePlanResultListener {
            override fun onGetWalkingRouteResult(result: WalkingRouteResult?) {
                if (generation == requestGeneration && travelMode == RouteTravelMode.WALKING) {
                    finishRouteResult(result, result?.routeLines.orEmpty())
                }
            }

            override fun onGetBikingRouteResult(result: BikingRouteResult?) {
                if (generation == requestGeneration && travelMode == RouteTravelMode.CYCLING) {
                    finishRouteResult(result, result?.routeLines.orEmpty())
                }
            }

            override fun onGetTransitRouteResult(result: TransitRouteResult?) = Unit
            override fun onGetMassTransitRouteResult(result: MassTransitRouteResult?) = Unit
            override fun onGetDrivingRouteResult(result: DrivingRouteResult?) = Unit
            override fun onGetIndoorRouteResult(result: IndoorRouteResult?) = Unit
            override fun onGetIntegralRouteResult(result: IntegralRouteResult?) = Unit
        })

        val from = PlanNode.withLocation(origin)
        val to = PlanNode.withLocation(destination)
        val accepted = when (travelMode) {
            RouteTravelMode.WALKING -> search.walkingSearch(
                WalkingRoutePlanOption().from(from).to(to)
            )
            RouteTravelMode.CYCLING -> search.bikingSearch(
                BikingRoutePlanOption().from(from).to(to).ridingType(0)
            )
        }
        if (!accepted) {
            finish(Result.failure(IllegalStateException("路线规划请求参数无效")))
        }
    }

    fun cancel() {
        requestGeneration += 1
        callback = null
        routeSearch?.destroy()
        routeSearch = null
    }

    fun destroy() = cancel()

    private fun <T : RouteStep> finishRouteResult(
        result: SearchResult?,
        lines: List<RouteLine<T>>
    ) {
        if (result?.error != SearchResult.ERRORNO.NO_ERROR) {
            finish(Result.failure(IllegalStateException(routeError(result?.error))))
            return
        }
        val origin = pendingOrigin
        val destination = pendingDestination
        if (origin == null || destination == null) {
            finish(Result.failure(IllegalStateException("路线起终点无效")))
            return
        }

        val usableLines = lines.filter { line ->
            line.allStep.orEmpty().any { it.wayPoints.orEmpty().isNotEmpty() }
        }
        val selectedLine = when (pendingPreference) {
            RoutePreference.SHORTEST -> usableLines.minWithOrNull(
                compareBy<RouteLine<T>> { it.distance }.thenBy { it.duration }
            )
            RoutePreference.FASTEST -> usableLines.minWithOrNull(
                compareBy<RouteLine<T>> { it.duration }.thenBy { it.distance }
            )
        }
        if (selectedLine == null) {
            finish(Result.failure(IllegalStateException("未找到可用路线")))
            return
        }

        val points = buildList {
            add(origin)
            selectedLine.allStep.orEmpty().forEach { addAll(it.wayPoints.orEmpty()) }
            add(destination)
        }.distinctConsecutive()

        finish(
            Result.success(
                PlannedRoute(
                    points = points,
                    distanceMeters = selectedLine.distance,
                    durationSeconds = selectedLine.duration,
                    travelMode = pendingMode,
                    preference = pendingPreference
                )
            )
        )
    }

    private fun finish(result: Result<PlannedRoute>) {
        routeSearch?.destroy()
        routeSearch = null
        callback.also { callback = null }?.invoke(result)
    }

    private fun routeError(error: SearchResult.ERRORNO?): String = when (error) {
        SearchResult.ERRORNO.NETWORK_ERROR,
        SearchResult.ERRORNO.NETWORK_TIME_OUT -> "路线规划失败（网络连接异常）"
        SearchResult.ERRORNO.KEY_ERROR,
        SearchResult.ERRORNO.PERMISSION_UNFINISHED -> "路线规划失败（百度地图 AK 校验失败）"
        SearchResult.ERRORNO.RESULT_NOT_FOUND -> "没有找到可用路线"
        null -> "路线规划暂时不可用"
        else -> "路线规划失败（百度错误：$error）"
    }
}

private fun List<LatLng>.distinctConsecutive(): List<LatLng> = buildList {
    this@distinctConsecutive.forEach { point ->
        val previous = lastOrNull()
        if (previous == null || previous.latitude != point.latitude || previous.longitude != point.longitude) {
            add(point)
        }
    }
}
