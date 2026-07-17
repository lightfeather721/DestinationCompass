package com.destinationcompass.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import com.destinationcompass.app.domain.BearingCalculator
import com.destinationcompass.app.domain.CompassProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/** The single owner of device heading sensors and magnetic-heading filtering. */
class CompassSensorManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gameRotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val headingSensor = rotationVectorSensor ?: gameRotationVectorSensor
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val processor = CompassProcessor()

    private var reportedAccuracy = CompassAccuracy.UNKNOWN
    private var magneticAnomaly = false
    private var anomalyScore = 0
    private var started = false
    private var startElapsedMillis = 0L
    private var firstCallbackLogged = false
    private var firstHeadingLogged = false
    private var lastDebugLogMillis = 0L
    private var lastDisplayRotation: Int? = null
    private var lastPublishedTimestampNanos = 0L

    private val _state = MutableStateFlow(
        CompassState(
            isAvailable = headingSensor != null,
            sensorType = headingSensor?.type,
            usesMagneticNorth = rotationVectorSensor != null
        )
    )
    val state: StateFlow<CompassState> = _state.asStateFlow()
    val available: Boolean = headingSensor != null

    fun start() {
        if (started) return
        val sensor = headingSensor
        if (sensor == null) {
            _state.value = _state.value.copy(
                isAvailable = false,
                accuracy = CompassAccuracy.UNRELIABLE,
                hasValidHeading = false
            )
            Log.w(TAG, "sensor unavailable: rotation vector and game rotation vector missing")
            return
        }

        processor.reset()
        startElapsedMillis = SystemClock.elapsedRealtime()
        firstCallbackLogged = false
        firstHeadingLogged = false
        lastDebugLogMillis = 0L
        lastDisplayRotation = null
        lastPublishedTimestampNanos = 0L
        started = sensorManager.registerListener(this, sensor, SENSOR_SAMPLING_PERIOD_US)
        if (!started) {
            _state.value = _state.value.copy(isAvailable = false, hasValidHeading = false)
            Log.w(TAG, "failed to register sensor=${sensorName(sensor.type)}")
            return
        }
        magneticSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        Log.d(
            TAG,
            "registered sensor=${sensorName(sensor.type)}, magneticNorth=${rotationVectorSensor != null}, samplingUs=$SENSOR_SAMPLING_PERIOD_US"
        )
    }

    fun stop() {
        if (!started) return
        sensorManager.unregisterListener(this)
        started = false
        Log.d(TAG, "unregistered sensors")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started) return
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            updateMagneticFieldQuality(event.values)
            return
        }
        if (event.sensor.type != headingSensor?.type) return

        val nowMillis = SystemClock.elapsedRealtime()
        if (!firstCallbackLogged) {
            firstCallbackLogged = true
            Log.d(TAG, "first callback after=${nowMillis - startElapsedMillis}ms sensor=${sensorName(event.sensor.type)}")
        }

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val displayRotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        if (lastDisplayRotation != null && lastDisplayRotation != displayRotation) {
            // A coordinate-system change is not a physical turn; initialize directly in the
            // new axes instead of filtering through an artificial 90°/180° jump.
            processor.reset()
            Log.d(TAG, "display rotation changed ${lastDisplayRotation}->$displayRotation; filter reset")
        }
        lastDisplayRotation = displayRotation
        val (axisX, axisY) = axesForDisplayRotation(displayRotation)
        if (!SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)) {
            Log.w(TAG, "remapCoordinateSystem failed rotation=$displayRotation")
            return
        }
        SensorManager.getOrientation(remappedMatrix, orientationAngles)
        val rawHeading = BearingCalculator.normalizeDegrees(
            Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        )
        if (!rawHeading.isFinite()) return

        val eventTimestamp = event.timestamp.takeIf { it > 0L } ?: SystemClock.elapsedRealtimeNanos()
        val filteredHeading = processor.process(rawHeading, eventTimestamp)
        if (!filteredHeading.isFinite()) return
        // Process every hardware sample, but publish at a stable UI cadence. There is no
        // angle-delta gate, so slow turns and north-crossing updates can never become stuck.
        if (lastPublishedTimestampNanos != 0L &&
            eventTimestamp - lastPublishedTimestampNanos < UI_PUBLISH_INTERVAL_NANOS
        ) return
        lastPublishedTimestampNanos = eventTimestamp

        val state = CompassState(
            heading = filteredHeading,
            rawHeading = rawHeading,
            accuracy = effectiveAccuracy(),
            isAvailable = true,
            timestampMillis = System.currentTimeMillis(),
            hasValidHeading = true,
            sensorType = event.sensor.type,
            usesMagneticNorth = rotationVectorSensor != null
        )
        _state.value = state

        if (!firstHeadingLogged) {
            firstHeadingLogged = true
            Log.d(TAG, "first valid heading after=${nowMillis - startElapsedMillis}ms raw=$rawHeading displayed=$filteredHeading")
        }
        if (nowMillis - lastDebugLogMillis >= DEBUG_LOG_INTERVAL_MILLIS) {
            lastDebugLogMillis = nowMillis
            Log.d(
                TAG,
                "raw=$rawHeading filtered=$filteredHeading displayed=$filteredHeading accuracy=${state.accuracy} rotation=$displayRotation sensor=${sensorName(event.sensor.type)}"
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (!started) return
        val contributesToAbsoluteHeading = sensor?.type == headingSensor?.type ||
            (rotationVectorSensor != null && sensor?.type == Sensor.TYPE_MAGNETIC_FIELD)
        if (!contributesToAbsoluteHeading) return
        reportedAccuracy = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
            SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassAccuracy.UNRELIABLE
            else -> CompassAccuracy.UNKNOWN
        }
        // Accuracy is status-only. LOW/MEDIUM never blocks a valid heading update.
        _state.value = _state.value.copy(accuracy = effectiveAccuracy())
    }

    private fun updateMagneticFieldQuality(values: FloatArray) {
        val magnitude = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        anomalyScore = if (magnitude !in 20f..75f) {
            (anomalyScore + 1).coerceAtMost(8)
        } else {
            (anomalyScore - 1).coerceAtLeast(0)
        }
        val anomalyNow = anomalyScore >= 5
        if (magneticAnomaly != anomalyNow) {
            magneticAnomaly = anomalyNow
            _state.value = _state.value.copy(accuracy = effectiveAccuracy())
        }
    }

    private fun effectiveAccuracy(): CompassAccuracy =
        if (magneticAnomaly) CompassAccuracy.LOW else reportedAccuracy

    private fun axesForDisplayRotation(rotation: Int): Pair<Int, Int> = when (rotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }

    private fun sensorName(type: Int): String = when (type) {
        Sensor.TYPE_ROTATION_VECTOR -> "TYPE_ROTATION_VECTOR"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "TYPE_GAME_ROTATION_VECTOR"
        else -> "type=$type"
    }

    private companion object {
        const val TAG = "CompassDebug"
        const val SENSOR_SAMPLING_PERIOD_US = 20_000
        const val UI_PUBLISH_INTERVAL_NANOS = 33_000_000L
        const val DEBUG_LOG_INTERVAL_MILLIS = 500L
    }
}
