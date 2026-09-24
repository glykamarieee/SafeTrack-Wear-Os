package com.safetrack.watch.services.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The configured SOS shake gesture: several strong, quick back-and-forth
 * shakes of the wrist. Walking, running and ordinary arm movement stay below
 * these thresholds; anything that still gets through is caught by the SOS
 * countdown, which the child can cancel.
 */
data class ShakeConfig(
    /** Linear acceleration (gravity removed) a shake peak must reach, in m/s² (~2 g). */
    val peakAccelerationMs2: Float = 20f,
    /** Peaks needed within [windowMs]. */
    val requiredPeaks: Int = 6,
    val minPeakGapMs: Long = 90,
    /** A slower gap than this is not shaking; the count restarts. */
    val maxPeakGapMs: Long = 450,
    val windowMs: Long = 2_500,
    /** Wrist rotation speed that must accompany the shake, in rad/s (when a gyroscope exists). */
    val rotationRadS: Float = 4f,
    /** Ignore further shakes for this long after a trigger. */
    val cooldownMs: Long = 15_000,
)

class ShakeDetector(
    context: Context,
    private val config: ShakeConfig = ShakeConfig(),
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)

    // A wake-up accelerometer lets detection work with the screen off; events are
    // batched for up to a second, which keeps the CPU asleep between batches.
    private val accelerometer: Sensor? =
        sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val gravity = FloatArray(3)
    private var gravityReady = false
    private var peaks = 0
    private var firstPeakNs = 0L
    private var lastPeakNs = 0L
    private var shakeSeenNs = 0L
    private var rotationSeenNs = 0L
    private var lastTriggerNs = 0L
    private var running = false

    fun start() {
        if (running || accelerometer == null) return
        running = true
        reset()
        sensors?.registerListener(this, accelerometer, SAMPLING_US, BATCH_LATENCY_US)
        gyroscope?.let { sensors?.registerListener(this, it, SAMPLING_US, BATCH_LATENCY_US) }
        Log.i(TAG, "Shake SOS detection on (gyroscope=${gyroscope != null})")
    }

    fun stop() {
        if (!running) return
        running = false
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> onAcceleration(event)
            Sensor.TYPE_GYROSCOPE -> onRotation(event)
        }
        maybeTrigger(event.timestamp)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private fun onAcceleration(event: SensorEvent) {
        val v = event.values
        if (!gravityReady) {
            v.copyInto(gravity, endIndex = 3)
            gravityReady = true
            return
        }
        for (i in 0..2) gravity[i] = GRAVITY_ALPHA * gravity[i] + (1 - GRAVITY_ALPHA) * v[i]
        val x = v[0] - gravity[0]
        val y = v[1] - gravity[1]
        val z = v[2] - gravity[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        val now = event.timestamp
        if (magnitude < config.peakAccelerationMs2) return

        val gap = now - lastPeakNs
        if (lastPeakNs != 0L && gap < ms(config.minPeakGapMs)) return

        if (lastPeakNs == 0L || gap > ms(config.maxPeakGapMs) || now - firstPeakNs > ms(config.windowMs)) {
            peaks = 1
            firstPeakNs = now
        } else {
            peaks++
        }
        lastPeakNs = now

        if (peaks >= config.requiredPeaks) {
            shakeSeenNs = now
            peaks = 0
            lastPeakNs = 0L
        }
    }

    private fun onRotation(event: SensorEvent) {
        val v = event.values
        val speed = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (speed >= config.rotationRadS) rotationSeenNs = event.timestamp
    }

    private fun maybeTrigger(nowNs: Long) {
        if (shakeSeenNs == 0L) return
        if (nowNs - shakeSeenNs > ms(config.windowMs)) {
            shakeSeenNs = 0L
            return
        }
        // Accelerometer and gyroscope batches may arrive separately; match them by timestamp.
        val rotationOk = gyroscope == null ||
            (rotationSeenNs != 0L && abs(shakeSeenNs - rotationSeenNs) <= ms(config.windowMs))
        if (!rotationOk) return
        if (lastTriggerNs != 0L && shakeSeenNs - lastTriggerNs < ms(config.cooldownMs)) {
            shakeSeenNs = 0L
            return
        }

        lastTriggerNs = shakeSeenNs
        shakeSeenNs = 0L
        Log.i(TAG, "SOS shake gesture detected")
        onShake()
    }

    private fun reset() {
        peaks = 0
        firstPeakNs = 0L
        lastPeakNs = 0L
        shakeSeenNs = 0L
        rotationSeenNs = 0L
        gravityReady = false
    }

    private fun ms(value: Long) = value * 1_000_000L

    private companion object {
        const val TAG = "ShakeDetector"
        const val SAMPLING_US = 20_000 // 50 Hz
        const val BATCH_LATENCY_US = 1_000_000
        const val GRAVITY_ALPHA = 0.8f
    }
}
