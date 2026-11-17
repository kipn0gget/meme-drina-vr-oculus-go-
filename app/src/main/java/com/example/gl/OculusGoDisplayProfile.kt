package com.example.gl

import com.example.data.VrRefreshRate
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Hardware-calibrated Display & Optics Profile for Oculus Go (Gen 1):
 * - Single 5.5" Fast-Switch RGB LCD: 2560 x 1440 total (1280 x 1440 per eye)
 * - Low-persistence strobed backlight with 60 Hz standard & 72 Hz low-flicker modes
 * - Next-Gen Fresnel lenses with 90°–100° diagonal FoV (95° nominal) and god-ray softening
 * - Fixed 63.5 mm Interpupillary Distance (IPD = 0.0635 m, ±0.03175 m per eye)
 */
object OculusGoDisplayProfile {
    const val PANEL_WIDTH_PX = 2560
    const val PANEL_HEIGHT_PX = 1440
    const val PER_EYE_WIDTH_PX = 1280
    const val PER_EYE_HEIGHT_PX = 1440
    const val NATIVE_EYE_ASPECT = PER_EYE_WIDTH_PX.toFloat() / PER_EYE_HEIGHT_PX.toFloat()

    // Fixed lens IPD: 63.5 mm
    const val FIXED_IPD_METERS = 0.0635f
    const val HALF_IPD_METERS = FIXED_IPD_METERS * 0.5f // 0.03175f

    // Next-Gen Fresnel diagonal Field of View (90° - 100° spec)
    const val DIAGONAL_FOV_DEG = 95f

    // Fast-Switch RGB LCD 72 Hz color vibrancy & Fresnel anti-god-ray tone ceiling
    const val COLOR_VIBRANCY_72HZ = 1.07f
    const val COLOR_VIBRANCY_60HZ = 1.00f
    const val FRESNEL_PEAK_CLAMP = 0.94f

    /**
     * Derives the vertical perspective FoV for an eye viewport aspect ratio so the
     * diagonal Field of View matches the Oculus Go Next-Gen Fresnel 95° optic.
     */
    fun calculateVerticalFovDeg(
        eyeAspect: Float,
        diagonalFovDeg: Float = DIAGONAL_FOV_DEG
    ): Float {
        val safeAspect = eyeAspect.coerceIn(0.5f, 2.2f)
        val halfDiagRad = (diagonalFovDeg * 0.5f) * (PI.toFloat() / 180f)
        val tanHalfDiag = tan(halfDiagRad)
        val tanHalfVert = tanHalfDiag / sqrt(1f + safeAspect * safeAspect)
        return (2f * atan(tanHalfVert) * 180f / PI.toFloat()).coerceIn(60f, 96f)
    }

    fun vibrancyFor(refreshRate: VrRefreshRate): Float = when (refreshRate) {
        VrRefreshRate.HZ_72 -> COLOR_VIBRANCY_72HZ
        VrRefreshRate.HZ_60 -> COLOR_VIBRANCY_60HZ
    }
}
