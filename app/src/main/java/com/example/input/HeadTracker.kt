package com.example.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ImuStatus(
    val hasGyroscope: Boolean = false,
    val hasAccelerometer: Boolean = false,
    val hasMagnetometer: Boolean = false,
    val using9DofCompassFusion: Boolean = false
) {
    val label: String
        get() = when {
            using9DofCompassFusion || (hasGyroscope && hasAccelerometer && hasMagnetometer) -> "9-DoF IMU"
            hasGyroscope && hasAccelerometer -> "6-DoF IMU"
            hasAccelerometer && hasMagnetometer -> "Compass"
            else -> "Touch"
        }
}

/**
 * 3DoF VR Head Tracker supporting the Oculus Go 9-axis IMU:
 * - Gyroscope: high-rate 3-axis angular velocity (yaw, pitch, roll)
 * - Accelerometer: gravity reference for true "up" and "down" horizon alignment
 * - Magnetometer: magnetic compass reference to eliminate gyroscope yaw drift
 */
class HeadTracker(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationVectorSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gameRotationVectorSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private val gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val imuStatus: ImuStatus = ImuStatus(
        hasGyroscope = gyroSensor != null || rotationVectorSensor != null || gameRotationVectorSensor != null,
        hasAccelerometer = accelSensor != null || rotationVectorSensor != null || gameRotationVectorSensor != null,
        hasMagnetometer = magSensor != null || rotationVectorSensor != null,
        using9DofCompassFusion = rotationVectorSensor != null || (gyroSensor != null && accelSensor != null && magSensor != null)
    )

    val hasHardwareGyro: Boolean =
        rotationVectorSensor != null ||
            gameRotationVectorSensor != null ||
            gyroSensor != null ||
            (accelSensor != null && magSensor != null)

    @Volatile var enabled: Boolean = true
    @Volatile var sensitivity: Float = 1.0f
    @Volatile var displayRotation: Int = Surface.ROTATION_90

    @Volatile var yawOffsetRad: Float = 0f
        private set
    @Volatile var pitchOffsetRad: Float = 0f
        private set
    @Volatile var rollRad: Float = 0f
        private set

    private val rawMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // Raw 3-sensor complementary fusion state (used when TYPE_ROTATION_VECTOR is absent)
    private val fusedMatrix = FloatArray(9).apply {
        this[0] = 1f; this[4] = 1f; this[8] = 1f
    }
    private val accelMagMatrix = FloatArray(9)
    private val accelSample = FloatArray(3)
    private val magSample = FloatArray(3)
    private var hasAccelSample = false
    private var hasMagSample = false
    private var hasFusedInit = false
    private var lastGyroTimestampNs = 0L

    private var referenceYawRad = 0f
    private var hasReferenceYaw = false
    private var recenterRequested = true

    fun updateDisplayRotation(rotation: Int? = null) {
        val resolved = rotation ?: queryDisplayRotation()
        if (displayRotation != resolved) {
            displayRotation = resolved
            recenterRequested = true
        }
    }

    @Suppress("DEPRECATION")
    private fun queryDisplayRotation(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.rotation ?: Surface.ROTATION_90
            } else {
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                    ?.defaultDisplay?.rotation ?: Surface.ROTATION_90
            }
        } catch (_: Throwable) {
            Surface.ROTATION_90
        }
    }

    fun start() {
        updateDisplayRotation()
        recenterRequested = true
        lastGyroTimestampNs = 0L
        hasFusedInit = false

        val sm = sensorManager ?: return
        // Prefer 9-DoF TYPE_ROTATION_VECTOR (Gyroscope + Accelerometer + Magnetometer) for drift-free yaw.
        val rv = rotationVectorSensor
        if (rv != null) {
            sm.registerListener(this, rv, SensorManager.SENSOR_DELAY_FASTEST)
            return
        }

        // Fallback to raw 3-sensor complementary fusion if all three hardware sensors exist, or 6-DoF Game Rotation Vector.
        if (gyroSensor != null && accelSensor != null && magSensor != null) {
            sm.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST)
            sm.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            sm.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_GAME)
            return
        }

        val grv = gameRotationVectorSensor
        if (grv != null) {
            sm.registerListener(this, grv, SensorManager.SENSOR_DELAY_FASTEST)
            return
        }

        // Last-resort raw sensor registration (e.g., Accelerometer + Magnetometer or Gyro-only)
        gyroSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        accelSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        lastGyroTimestampNs = 0L
    }

    fun recenter() {
        recenterRequested = true
        yawOffsetRad = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!enabled) {
            yawOffsetRad = 0f
            pitchOffsetRad = 0f
            rollRad = 0f
            return
        }

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rawMatrix, event.values)
                updateOrientationFromDeviceWorldMatrix(rawMatrix)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Low-pass filter accelerometer for smooth gravity estimation
                val alpha = if (hasAccelSample) 0.25f else 1.0f
                accelSample[0] = accelSample[0] + alpha * (event.values[0] - accelSample[0])
                accelSample[1] = accelSample[1] + alpha * (event.values[1] - accelSample[1])
                accelSample[2] = accelSample[2] + alpha * (event.values[2] - accelSample[2])
                hasAccelSample = true

                if (gyroSensor == null && hasMagSample) {
                    if (SensorManager.getRotationMatrix(accelMagMatrix, null, accelSample, magSample)) {
                        updateOrientationFromDeviceWorldMatrix(accelMagMatrix)
                    }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val alpha = if (hasMagSample) 0.20f else 1.0f
                magSample[0] = magSample[0] + alpha * (event.values[0] - magSample[0])
                magSample[1] = magSample[1] + alpha * (event.values[1] - magSample[1])
                magSample[2] = magSample[2] + alpha * (event.values[2] - magSample[2])
                hasMagSample = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (lastGyroTimestampNs == 0L) {
                    lastGyroTimestampNs = event.timestamp
                    if (!hasFusedInit && hasAccelSample && hasMagSample &&
                        SensorManager.getRotationMatrix(accelMagMatrix, null, accelSample, magSample)
                    ) {
                        System.arraycopy(accelMagMatrix, 0, fusedMatrix, 0, 9)
                        hasFusedInit = true
                    }
                    return
                }
                val dt = ((event.timestamp - lastGyroTimestampNs) * 1e-9f).coerceIn(0f, 0.1f)
                lastGyroTimestampNs = event.timestamp
                stepComplementaryFusion(
                    gx = event.values[0],
                    gy = event.values[1],
                    gz = event.values[2],
                    dt = dt
                )
                updateOrientationFromDeviceWorldMatrix(fusedMatrix)
            }
        }
    }

    /**
     * Advances the raw 9-axis IMU fusion filter using:
     * 1) Gyroscope integration for high-rate 3-axis rotational tracking
     * 2) Accelerometer + Magnetometer gravity & compass drift correction when linear acceleration is low
     */
    internal fun stepComplementaryFusion(gx: Float, gy: Float, gz: Float, dt: Float) {
        if (!hasFusedInit && hasAccelSample && hasMagSample &&
            SensorManager.getRotationMatrix(accelMagMatrix, null, accelSample, magSample)
        ) {
            System.arraycopy(accelMagMatrix, 0, fusedMatrix, 0, 9)
            hasFusedInit = true
        }

        if (dt > 0f) {
            integrateGyroIntoMatrix(fusedMatrix, gx, gy, gz, dt)
        }

        if (hasAccelSample) {
            val accelNorm = sqrt(
                accelSample[0] * accelSample[0] +
                    accelSample[1] * accelSample[1] +
                    accelSample[2] * accelSample[2]
            )
            // Only apply gravity/compass drift correction when not undergoing strong linear acceleration
            if (accelNorm in 7.8f..11.8f) {
                if (hasMagSample && SensorManager.getRotationMatrix(accelMagMatrix, null, accelSample, magSample)) {
                    val gain = if (hasFusedInit) 0.025f else 1.0f
                    for (i in 0..8) {
                        fusedMatrix[i] = fusedMatrix[i] * (1f - gain) + accelMagMatrix[i] * gain
                    }
                    orthonormalizeMatrix(fusedMatrix)
                    hasFusedInit = true
                } else {
                    // Gravity-only (Accelerometer) correction keeps "up" and "down" level without magnetometer
                    val ux = accelSample[0] / accelNorm
                    val uy = accelSample[1] / accelNorm
                    val uz = accelSample[2] / accelNorm
                    val gain = if (hasFusedInit) 0.025f else 1.0f
                    fusedMatrix[6] = fusedMatrix[6] * (1f - gain) + ux * gain
                    fusedMatrix[7] = fusedMatrix[7] * (1f - gain) + uy * gain
                    fusedMatrix[8] = fusedMatrix[8] * (1f - gain) + uz * gain
                    orthonormalizeRowsFromUp(fusedMatrix)
                    hasFusedInit = true
                }
            }
        }
    }

    internal fun setRawAccelMagForTest(
        ax: Float, ay: Float, az: Float,
        mx: Float, my: Float, mz: Float
    ) {
        accelSample[0] = ax; accelSample[1] = ay; accelSample[2] = az
        magSample[0] = mx; magSample[1] = my; magSample[2] = mz
        hasAccelSample = true
        hasMagSample = true
    }

    internal fun updateOrientationFromDeviceWorldMatrix(deviceToWorld: FloatArray) {
        // Remap into VR Horizon frame (X=Right, Y=Forward into scene, Z=Up) for active display rotation
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_0 -> SensorManager.AXIS_X to SensorManager.AXIS_MINUS_Z
            Surface.ROTATION_90 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_MINUS_Z
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z
            Surface.ROTATION_270 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_Z
            else -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_MINUS_Z
        }
        SensorManager.remapCoordinateSystem(deviceToWorld, axisX, axisY, remappedMatrix)
        SensorManager.getOrientation(remappedMatrix, orientation)

        val worldYaw = orientation[0]
        val worldPitch = orientation[1]
        val worldRoll = orientation[2]

        // Recenter resets ONLY world yaw; pitch & roll remain anchored to real-world gravity (Accelerometer)
        if (recenterRequested || !hasReferenceYaw) {
            referenceYawRad = worldYaw
            hasReferenceYaw = true
            recenterRequested = false
        }

        yawOffsetRad = wrapAngleRad(worldYaw - referenceYawRad) * sensitivity
        pitchOffsetRad = (-worldPitch * sensitivity).coerceIn(-1.25f, 1.25f)
        rollRad = (-worldRoll * 0.65f).coerceIn(-0.45f, 0.45f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        internal fun wrapAngleRad(angle: Float): Float {
            var a = angle
            val twoPi = (2.0 * PI).toFloat()
            val pi = PI.toFloat()
            while (a > pi) a -= twoPi
            while (a < -pi) a += twoPi
            return a
        }

        internal fun integrateGyroIntoMatrix(r: FloatArray, gx: Float, gy: Float, gz: Float, dt: Float) {
            val omegaMag = sqrt(gx * gx + gy * gy + gz * gz)
            val theta = omegaMag * dt
            if (theta < 1e-6f) return

            val ux = gx / omegaMag
            val uy = gy / omegaMag
            val uz = gz / omegaMag
            val c = cos(theta)
            val s = sin(theta)
            val oneMinusC = 1f - c

            val d00 = c + ux * ux * oneMinusC
            val d01 = ux * uy * oneMinusC - uz * s
            val d02 = ux * uz * oneMinusC + uy * s
            val d10 = uy * ux * oneMinusC + uz * s
            val d11 = c + uy * uy * oneMinusC
            val d12 = uy * uz * oneMinusC - ux * s
            val d20 = uz * ux * oneMinusC - uy * s
            val d21 = uz * uy * oneMinusC + ux * s
            val d22 = c + uz * uz * oneMinusC

            val r00 = r[0]; val r01 = r[1]; val r02 = r[2]
            val r10 = r[3]; val r11 = r[4]; val r12 = r[5]
            val r20 = r[6]; val r21 = r[7]; val r22 = r[8]

            r[0] = r00 * d00 + r01 * d10 + r02 * d20
            r[1] = r00 * d01 + r01 * d11 + r02 * d21
            r[2] = r00 * d02 + r01 * d12 + r02 * d22

            r[3] = r10 * d00 + r11 * d10 + r12 * d20
            r[4] = r10 * d01 + r11 * d11 + r12 * d21
            r[5] = r10 * d02 + r11 * d12 + r12 * d22

            r[6] = r20 * d00 + r21 * d10 + r22 * d20
            r[7] = r20 * d01 + r21 * d11 + r22 * d21
            r[8] = r20 * d02 + r21 * d12 + r22 * d22

            orthonormalizeMatrix(r)
        }

        internal fun orthonormalizeMatrix(m: FloatArray) {
            // Orthonormalize column vectors (col0, col1, col2 = col0 x col1)
            var x0 = m[0]; var x1 = m[3]; var x2 = m[6]
            val xLen = sqrt(x0 * x0 + x1 * x1 + x2 * x2).coerceAtLeast(1e-6f)
            x0 /= xLen; x1 /= xLen; x2 /= xLen

            var y0 = m[1]; var y1 = m[4]; var y2 = m[7]
            val dot = x0 * y0 + x1 * y1 + x2 * y2
            y0 -= dot * x0; y1 -= dot * x1; y2 -= dot * x2
            val yLen = sqrt(y0 * y0 + y1 * y1 + y2 * y2).coerceAtLeast(1e-6f)
            y0 /= yLen; y1 /= yLen; y2 /= yLen

            val z0 = x1 * y2 - x2 * y1
            val z1 = x2 * y0 - x0 * y2
            val z2 = x0 * y1 - x1 * y0

            m[0] = x0; m[3] = x1; m[6] = x2
            m[1] = y0; m[4] = y1; m[7] = y2
            m[2] = z0; m[5] = z1; m[8] = z2
        }

        internal fun orthonormalizeRowsFromUp(m: FloatArray) {
            // Row 2 is World Up in device coordinates
            var u0 = m[6]; var u1 = m[7]; var u2 = m[8]
            val uLen = sqrt(u0 * u0 + u1 * u1 + u2 * u2).coerceAtLeast(1e-6f)
            u0 /= uLen; u1 /= uLen; u2 /= uLen

            // Row 1 (North) = normalize(Up x East)
            val e0 = m[0]; val e1 = m[1]; val e2 = m[2]
            var n0 = u1 * e2 - u2 * e1
            var n1 = u2 * e0 - u0 * e2
            var n2 = u0 * e1 - u1 * e0
            val nLen = sqrt(n0 * n0 + n1 * n1 + n2 * n2).coerceAtLeast(1e-6f)
            n0 /= nLen; n1 /= nLen; n2 /= nLen

            // Row 0 (East) = North x Up
            val r0 = n1 * u2 - n2 * u1
            val r1 = n2 * u0 - n0 * u2
            val r2 = n0 * u1 - n1 * u0

            m[0] = r0; m[1] = r1; m[2] = r2
            m[3] = n0; m[4] = n1; m[5] = n2
            m[6] = u0; m[7] = u1; m[8] = u2
        }
    }
}
