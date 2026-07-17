package com.destinationcompass.app.data.map

import android.content.Context
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer

/** Defers the comparatively expensive map SDK startup until the map is opened. */
object BaiduMapSdkInitializer {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val applicationContext = context.applicationContext
            SDKInitializer.setAgreePrivacy(applicationContext, true)
            SDKInitializer.initialize(applicationContext)
            SDKInitializer.setCoordType(CoordType.GCJ02)
            initialized = true
        }
    }
}
