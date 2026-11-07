package com.safetrack.watch.services.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Which way the watch faces (0 = true north, clockwise), for the direction
 * arrow on the guardian's map while the child stands still (GPS gives a
 * direction only while moving).
 *
 * Runs only while a guardian has the map open (LocationTracker starts and
 * stops it), at the UI sensor rate: the rotation-vector sensor is a low-power
 * fused sensor. Readings are smoothed so the arrow doesn't jitter, and dropped
 * while the sensor reports it needs calibrating.
 */
class HeadingTracker(context: Context, private val onChange: (Float) -> Unit) : SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensors?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private var running = false
    private var unreliable = false

    // Smoothed direction as a unit vector (averaging angles directly breaks at 0/360).
    private var x = 0f
    private var y = 0f
    private var hasValue = false

    @Volatile
    private var declination = 0f

    /** The newest smoothed heading, or null without a usable compass. */
    @Volatile
    var heading: Float? = null
        private set

    val available: Boolean get() = sensor != null

    fun start() {
        if (running || sensor == null) return
        running = sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI) == true
        if (running) Log.i(TAG, "Compass on")
    }

    fun stop() {
        if (!running) return
        sensors?.unregisterListener(this)
        running = false
        hasValue = false
        heading = null
        Log.i(TAG, "Compass off")
    }

    /** Magnetic north -> true north for where the child is (about -1° in Manila). */
    fun updatePosition(latitude: Double, longitude: Double, altitude: Double = 0.0) {
        declination = GeomagneticField(latitude.toFloat(), longitude.toFloat(), altitude.toFloat(), System.currentTimeMillis()).declination
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (unreliable) return
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat() + declination
        val radians = Math.toRadians(azimuth.toDouble())
        val nx = cos(radians).toFloat()
        val ny = sin(radians).toFloat()
        if (!hasValue) {
            x = nx
            y = ny
            hasValue = true
        } else {
            x += SMOOTHING * (nx - x)
            y += SMOOTHING * (ny - y)
        }
        val next = ((Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat() % 360f) + 360f) % 360f
        val previous = heading
        heading = next
        if (previous == null || angleBetween(previous, next) >= REPORT_STEP_DEG) onChange(next)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        unreliable = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
        if (unreliable) heading = null
    }

    companion object {
        private const val TAG = "HeadingTracker"
        private const val SMOOTHING = 0.25f
        private const val REPORT_STEP_DEG = 2f

        fun angleBetween(a: Float, b: Float): Float = abs((((a - b) % 360f) + 540f) % 360f - 180f)
    }
}
