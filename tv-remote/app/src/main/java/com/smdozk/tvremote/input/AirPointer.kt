package com.smdozk.tvremote.input

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// Magic Remote style pointing: turning the phone left/right around the gravity axis moves the cursor
// sideways, tilting it up/down moves it vertically, and twisting it is ignored. Because the axes come
// from gravity, this holds whether the phone is held flat like a remote or upright like a camera.
class AirPointer(bias: FloatArray? = null, private val onMove: (Float, Float) -> Unit) {

    sealed interface Calibration {
        data class Measuring(val progress: Float) : Calibration
        class Done(val bias: FloatArray) : Calibration
        data object Failed : Calibration
    }

    var speed = 1f
    var onCalibration: (Calibration) -> Unit = {}

    // Display rotation in degrees (0, 90, 180, 270): picks which phone axis is "right" on screen.
    var screenRotation = 0
        set(value) {
            field = ((value % 360) + 360) % 360
            val (r, u) = when (field) {
                90 -> floatArrayOf(0f, -1f, 0f) to floatArrayOf(1f, 0f, 0f)
                180 -> floatArrayOf(-1f, 0f, 0f) to floatArrayOf(0f, -1f, 0f)
                270 -> floatArrayOf(0f, 1f, 0f) to floatArrayOf(-1f, 0f, 0f)
                else -> floatArrayOf(1f, 0f, 0f) to floatArrayOf(0f, 1f, 0f)
            }
            r.copyInto(screenRight)
            u.copyInto(screenUp)
        }

    val bias = FloatArray(3).also { b -> bias?.takeIf { it.size == 3 }?.copyInto(b) }

    private val screenRight = floatArrayOf(1f, 0f, 0f)
    private val screenUp = floatArrayOf(0f, 1f, 0f)
    private val up = FloatArray(3)
    private val right = FloatArray(3)
    private var hasUp = false

    private var lastNs = 0L
    private var yaw = 0f
    private var pitch = 0f
    private var stillNs = 0L
    private var held = false
    private var released = false
    private var muteUntilNs = 0L

    private var calibrating = false
    private var calStartNs = 0L
    private var calWindowNs = 0L
    private val calSum = DoubleArray(3)
    private var calCount = 0

    // Gravity in the phone's frame, as TYPE_GRAVITY / TYPE_ACCELEROMETER report it (points to the sky).
    fun onGravity(x: Float, y: Float, z: Float, smooth: Boolean = false) {
        val n = sqrt(x * x + y * y + z * z)
        if (n < 1f) return
        if (!hasUp || !smooth) {
            up[0] = x / n; up[1] = y / n; up[2] = z / n
        } else {
            // Raw accelerometer also carries hand acceleration: follow it slowly.
            up[0] += GRAVITY_SMOOTHING * (x / n - up[0])
            up[1] += GRAVITY_SMOOTHING * (y / n - up[1])
            up[2] += GRAVITY_SMOOTHING * (z / n - up[2])
            val m = sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2])
            up[0] /= m; up[1] /= m; up[2] /= m
        }
        hasUp = true
    }

    // Finger on the pad: the cursor stays put so pressing the screen cannot knock it off target.
    fun hold(on: Boolean) {
        if (on == held) return
        held = on
        yaw = 0f
        pitch = 0f
        if (!on) released = true
    }

    fun reset() {
        lastNs = 0L
        yaw = 0f
        pitch = 0f
        stillNs = 0L
    }

    fun startCalibration() {
        calibrating = true
        calStartNs = 0L
        restartWindow(0L)
        onCalibration(Calibration.Measuring(0f))
    }

    fun cancelCalibration() {
        calibrating = false
    }

    // Angular velocity in rad/s around the phone's x, y, z axes (TYPE_GYROSCOPE), timestamp in ns.
    fun onGyro(wx: Float, wy: Float, wz: Float, timestampNs: Long) {
        val previous = lastNs
        lastNs = timestampNs
        if (previous == 0L) return
        val dt = (timestampNs - previous) / 1e9f
        if (dt <= 0f || dt > MAX_DT_S) return

        if (calibrating) {
            calibrate(wx, wy, wz, timestampNs)
            return
        }

        val gx = wx - bias[0]
        val gy = wy - bias[1]
        val gz = wz - bias[2]
        trackBias(wx, wy, wz, gx, gy, gz, dt)

        if (released) {
            released = false
            muteUntilNs = timestampNs + RELEASE_MUTE_NS
        }
        if (held || !hasUp || timestampNs < muteUntilNs) return
        if (!updateRight()) return

        // Positive rotation about "up" turns the phone to the left, about "right" tips it up.
        val yawRate = gx * up[0] + gy * up[1] + gz * up[2]
        val pitchRate = gx * right[0] + gy * right[1] + gz * right[2]

        // Adaptive smoothing: strong at rest to hide hand tremor, light while moving so the cursor
        // starts and stops with the hand.
        val rawMagnitude = sqrt(yawRate * yawRate + pitchRate * pitchRate)
        val smoothMagnitude = sqrt(yaw * yaw + pitch * pitch)
        val cutoff = (MIN_CUTOFF_HZ + CUTOFF_PER_RAD * max(rawMagnitude, smoothMagnitude)).coerceAtMost(MAX_CUTOFF_HZ)
        val a = alpha(cutoff, dt)
        yaw += a * (yawRate - yaw)
        pitch += a * (pitchRate - pitch)

        val magnitude = sqrt(yaw * yaw + pitch * pitch)
        val deadzone = smoothstep(DEADZONE_LOW, DEADZONE_HIGH, magnitude)
        if (deadzone == 0f) return
        // Pointer acceleration: slow aiming is precise, a quick flick crosses the screen.
        val gain = speed * (SLOW_GAIN + (FAST_GAIN - SLOW_GAIN) * smoothstep(SLOW_RATE, FAST_RATE, magnitude))
        val dx = -yaw * gain * deadzone * dt
        val dy = -pitch * gain * deadzone * dt
        onMove(dx, dy)
    }

    // The screen's horizontal axis, flattened onto the horizon.
    private fun updateRight(): Boolean {
        val d = dot(screenRight, up)
        right[0] = screenRight[0] - d * up[0]
        right[1] = screenRight[1] - d * up[1]
        right[2] = screenRight[2] - d * up[2]
        var n = norm(right)
        if (n < SIDEWAYS_LIMIT) {
            // The screen's x axis points at the sky or floor (phone held on its side): use its y axis.
            val s = if (d > 0f) -1f else 1f
            val e = dot(screenUp, up)
            right[0] = s * (screenUp[0] - e * up[0])
            right[1] = s * (screenUp[1] - e * up[1])
            right[2] = s * (screenUp[2] - e * up[2])
            n = norm(right)
        }
        if (n < 1e-3f) return false
        right[0] /= n; right[1] /= n; right[2] /= n
        return true
    }

    // Slowly re-learns the gyro's zero point whenever the phone lies still, so the cursor never creeps.
    private fun trackBias(wx: Float, wy: Float, wz: Float, gx: Float, gy: Float, gz: Float, dt: Float) {
        val still = abs(gx) < STILL_RATE && abs(gy) < STILL_RATE && abs(gz) < STILL_RATE
        stillNs = if (still) stillNs + (dt * 1e9f).toLong() else 0L
        if (stillNs < STILL_BEFORE_LEARN_NS) return
        val k = (dt / BIAS_TIME_CONSTANT_S).coerceAtMost(1f)
        bias[0] = (bias[0] + k * (wx - bias[0])).coerceIn(-MAX_BIAS, MAX_BIAS)
        bias[1] = (bias[1] + k * (wy - bias[1])).coerceIn(-MAX_BIAS, MAX_BIAS)
        bias[2] = (bias[2] + k * (wz - bias[2])).coerceIn(-MAX_BIAS, MAX_BIAS)
    }

    private fun calibrate(wx: Float, wy: Float, wz: Float, now: Long) {
        if (calStartNs == 0L) {
            calStartNs = now
            restartWindow(now)
        }
        if (calCount > 0) {
            val moved = abs(wx - calSum[0] / calCount) > CAL_MOVE_TOLERANCE ||
                abs(wy - calSum[1] / calCount) > CAL_MOVE_TOLERANCE ||
                abs(wz - calSum[2] / calCount) > CAL_MOVE_TOLERANCE
            if (moved) restartWindow(now)
        }
        calSum[0] += wx.toDouble(); calSum[1] += wy.toDouble(); calSum[2] += wz.toDouble()
        calCount++
        val elapsed = now - calWindowNs
        if (elapsed >= CAL_WINDOW_NS && calCount >= CAL_MIN_SAMPLES) {
            calibrating = false
            val mean = FloatArray(3) { (calSum[it] / calCount).toFloat() }
            if (mean.any { abs(it) > MAX_BIAS }) {
                onCalibration(Calibration.Failed)
                return
            }
            mean.copyInto(bias)
            reset()
            onCalibration(Calibration.Done(mean.copyOf()))
        } else if (now - calStartNs > CAL_TIMEOUT_NS) {
            calibrating = false
            onCalibration(Calibration.Failed)
        } else {
            onCalibration(Calibration.Measuring((elapsed.toFloat() / CAL_WINDOW_NS).coerceIn(0f, 1f)))
        }
    }

    private fun restartWindow(now: Long) {
        calWindowNs = now
        calSum.fill(0.0)
        calCount = 0
    }

    private companion object {
        const val MAX_DT_S = 0.1f
        const val GRAVITY_SMOOTHING = 0.1f
        const val SIDEWAYS_LIMIT = 0.25f
        const val RELEASE_MUTE_NS = 100_000_000L

        const val MIN_CUTOFF_HZ = 2.5f
        const val CUTOFF_PER_RAD = 12f
        const val MAX_CUTOFF_HZ = 30f
        const val DEADZONE_LOW = 0.004f
        const val DEADZONE_HIGH = 0.02f

        // TV pixels per radian of rotation (webOS cursor space is 1920x1080).
        const val SLOW_GAIN = 1300f
        const val FAST_GAIN = 3600f
        const val SLOW_RATE = 0.08f
        const val FAST_RATE = 1.5f

        const val STILL_RATE = 0.02f
        const val STILL_BEFORE_LEARN_NS = 1_000_000_000L
        const val BIAS_TIME_CONSTANT_S = 2f
        const val MAX_BIAS = 0.15f

        const val CAL_WINDOW_NS = 1_500_000_000L
        const val CAL_TIMEOUT_NS = 10_000_000_000L
        const val CAL_MIN_SAMPLES = 20
        const val CAL_MOVE_TOLERANCE = 0.08f

        fun alpha(cutoffHz: Float, dt: Float): Float {
            val tau = 1f / (2f * Math.PI.toFloat() * cutoffHz)
            return 1f / (1f + tau / dt)
        }

        fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

        fun norm(a: FloatArray) = sqrt(dot(a, a))
    }
}
