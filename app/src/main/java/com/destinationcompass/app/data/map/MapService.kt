@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.destinationcompass.app.data.map

import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCodeOption
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener
import com.baidu.mapapi.search.poi.PoiCitySearchOption
import com.baidu.mapapi.search.poi.PoiDetailResult
import com.baidu.mapapi.search.poi.PoiDetailSearchResult
import com.baidu.mapapi.search.poi.PoiIndoorResult
import com.baidu.mapapi.search.poi.PoiResult
import com.baidu.mapapi.search.poi.PoiSearch
import com.destinationcompass.app.model.Destination

class MapService : OnGetGeoCoderResultListener, OnGetPoiSearchResultListener {
    private val geocoder = GeoCoder.newInstance()
    private val poiSearch = PoiSearch.newInstance()
    private var searchCallback: ((Result<List<Destination>>) -> Unit)? = null
    private var reverseCallback: ((Result<Destination>) -> Unit)? = null
    private var pendingQuery = ""
    private var pendingCity = DEFAULT_CITY
    private var reversePoint: LatLng? = null

    init {
        geocoder.setOnGetGeoCodeResultListener(this)
        poiSearch.setOnGetPoiSearchResultListener(this)
    }

    fun searchPlace(query: String, city: String = DEFAULT_CITY, callback: (Result<List<Destination>>) -> Unit) {
        pendingQuery = query.trim()
        pendingCity = city.ifBlank { DEFAULT_CITY }
        searchCallback = callback
        val accepted = poiSearch.searchInCity(
            PoiCitySearchOption()
                .city(pendingCity)
                .keyword(pendingQuery)
                .pageNum(0)
                .pageCapacity(20)
                .cityLimit(false)
        )
        if (!accepted) finishSearch(Result.failure(IllegalStateException("搜索请求参数无效")))
    }

    fun reverseGeocode(point: LatLng, callback: (Result<Destination>) -> Unit) {
        reversePoint = point
        reverseCallback = callback
        val accepted = geocoder.reverseGeoCode(
            ReverseGeoCodeOption()
                .location(point)
                .radius(300)
                .newVersion(1)
                .pageSize(10)
        )
        if (!accepted) finishReverse(Result.failure(IllegalStateException("选点请求参数无效")))
    }

    override fun onGetPoiResult(result: PoiResult?) {
        if (result?.error == SearchResult.ERRORNO.NO_ERROR) {
            val destinations = result.allPoi.orEmpty()
                .asSequence()
                .filter { it.location != null }
                .take(15)
                .map { item ->
                    Destination(
                        id = "poi-${item.uid.orEmpty().ifBlank { "${item.location.latitude},${item.location.longitude}" }}",
                        name = item.name.orEmpty().ifBlank { "搜索结果" },
                        address = listOf(item.city, item.area, item.address)
                            .filterNotNull()
                            .filter(String::isNotBlank)
                            .distinct()
                            .joinToString(" · "),
                        latitude = item.location.latitude,
                        longitude = item.location.longitude
                    )
                }
                .toList()
            if (destinations.isNotEmpty()) {
                finishSearch(Result.success(destinations))
            } else {
                geocodeFallback()
            }
        } else if (result?.error == SearchResult.ERRORNO.RESULT_NOT_FOUND) {
            geocodeFallback()
        } else {
            finishSearch(Result.failure(IllegalStateException(serviceError(result?.error, "搜索服务暂时不可用"))))
        }
    }

    override fun onGetGeoCodeResult(result: GeoCodeResult?) {
        val location = result?.location
        if (result?.error == SearchResult.ERRORNO.NO_ERROR && location != null) {
            finishSearch(
                Result.success(
                    listOf(
                        Destination(
                            name = pendingQuery.ifBlank { "搜索结果" },
                            address = result.address.orEmpty(),
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    )
                )
            )
        } else {
            finishSearch(Result.failure(IllegalStateException(serviceError(result?.error, "没有找到该地点"))))
        }
    }

    override fun onGetReverseGeoCodeResult(result: ReverseGeoCodeResult?) {
        val point = result?.location ?: reversePoint
        if (result?.error == SearchResult.ERRORNO.NO_ERROR && point != null) {
            val name = result.poiList.orEmpty().firstOrNull()?.name
                ?: result.sematicDescription
                ?: result.address
            finishReverse(
                Result.success(
                    Destination(
                        name = name.orEmpty().ifBlank { "地图选点" },
                        address = result.address.orEmpty(),
                        latitude = point.latitude,
                        longitude = point.longitude
                    )
                )
            )
        } else {
            finishReverse(Result.failure(IllegalStateException(serviceError(result?.error, "无法识别此位置"))))
        }
    }

    override fun onGetPoiDetailResult(result: PoiDetailResult?) = Unit
    override fun onGetPoiDetailResult(result: PoiDetailSearchResult?) = Unit
    override fun onGetPoiIndoorResult(result: PoiIndoorResult?) = Unit

    fun destroy() {
        searchCallback = null
        reverseCallback = null
        poiSearch.destroy()
        geocoder.destroy()
    }

    private fun geocodeFallback() {
        val accepted = geocoder.geocode(GeoCodeOption().city(pendingCity).address(pendingQuery))
        if (!accepted) finishSearch(Result.failure(IllegalStateException("没有找到该地点")))
    }

    private fun finishSearch(result: Result<List<Destination>>) {
        searchCallback.also { searchCallback = null }?.invoke(result)
    }

    private fun finishReverse(result: Result<Destination>) {
        reverseCallback.also { reverseCallback = null }?.invoke(result)
        reversePoint = null
    }

    private fun serviceError(error: SearchResult.ERRORNO?, fallback: String): String = when (error) {
        SearchResult.ERRORNO.KEY_ERROR,
        SearchResult.ERRORNO.PERMISSION_UNFINISHED -> "$fallback（百度地图 AK 校验失败）"
        SearchResult.ERRORNO.NETWORK_ERROR,
        SearchResult.ERRORNO.NETWORK_TIME_OUT -> "$fallback（网络连接异常）"
        null -> fallback
        else -> "$fallback（百度错误：$error）"
    }

    private companion object {
        const val DEFAULT_CITY = "全国"
    }
}
