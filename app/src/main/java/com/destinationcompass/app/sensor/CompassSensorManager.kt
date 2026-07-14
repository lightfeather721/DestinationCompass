package com.destinationcompass.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import com.destinationcompass.app.domain.BearingCalculator
import com.destinationcompass.app.domain.CompassProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class CompassSensorManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotationMatrix = FloatArray(9)
    private val adjustedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val processor = CompassProcessor()
    private var reportedAccuracy = CompassAccuracy.UNKNOWN
    private var magneticAnomaly = false
    private var anomalyScore = 0

    private val _state = MutableStateFlow(CompassState(isAvailable = rotationSensor != null))
    val state: StateFlow<CompassState> = _state.asStateFlow()
    val available: Boolean = rotationSensor != null

    fun start() {
        processor.reset()
        val sensor = rotationSensor
        if (sensor == null) {
            _state.value = CompassState(isAvailable = false, accuracy = CompassAccuracy.UNRELIABLE)
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        magneticSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            updateMagneticFieldQuality(event.values)
            return
        }
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val rotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        val axes = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axes.first, axes.second, adjustedMatrix)
        SensorManager.getOrientation(adjustedMatrix, orientation)
        val rawHeading = BearingCalculator.normalizeDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat())
        _state.value = CompassState(
            heading = processor.process(rawHeading),
            accuracy = effectiveAccuracy(),
            isAvailable = true,
            timestampMillis = System.currentTimeMillis()
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
        reportedAccuracy = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
            SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassAccuracy.UNRELIABLE
            else -> CompassAccuracy.UNKNOWN
        }
        _state.value = _state.value.copy(accuracy = effectiveAccuracy())
    }

    private fun updateMagneticFieldQuality(values: FloatArray) {
        val magnitude = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        anomalyScore = if (magnitude !in 20f..75f) {
            (anomalyScore + 1).coerceAtMost(8)
        } else {
            (anomalyScore - 1).coerceAtLeast(0)
        }
        val changed = magneticAnomaly != (anomalyScore >= 5)
        magneticAnomaly = anomalyScore >= 5
        if (changed) _state.value = _state.value.copy(accuracy = effectiveAccuracy())
    }

    private fun effectiveAccuracy(): CompassAccuracy =
        if (magneticAnomaly) CompassAccuracy.LOW else reportedAccuracy
}
