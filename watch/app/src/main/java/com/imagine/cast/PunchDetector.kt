package com.imagine.cast

import kotlin.math.sqrt

/**
 * Crude punch detector for Phase 0. Pure Kotlin with no Android dependencies, so it can be unit tested.
 *
 * The magnitude is sqrt(x² + y² + z²) of the raw TYPE_ACCELEROMETER sample, so it INCLUDES GRAVITY
 * (about 9.8 m/s² at rest). Gravity is deliberately not subtracted: Phase 0 only needs a crude spike,
 * and a real punch goes well above 18 m/s² whichever way the wrist points. Subtracting gravity would
 * need a filter or a second sensor, which isn't needed to answer the Phase 0 question.
 *
 * A punch is a sample whose magnitude is above [threshold], at least [cooldownMs] after the previous
 * punch. The cooldown also swallows the rest of the same spike, which lasts several samples.
 *
 * This class does not know about ARMED or IDLE. The caller only fires a fireball while ARMED.
 */
class PunchDetector(
    private val threshold: Float = PUNCH_THRESHOLD,
    private val cooldownMs: Long = PUNCH_COOLDOWN_MS,
    /** Used only when a sample has no time of its own. Monotonic, in ms. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var lastPunchMs: Long? = null

    /** Returns true if this sample is a punch. [tMs] must be monotonic (the sensor timestamp, in ms). */
    fun onSample(x: Float, y: Float, z: Float, tMs: Long = clock()): Boolean {
        // Magnitude includes gravity; see the class comment.
        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude <= threshold) return false
        val last = lastPunchMs
        if (last != null && tMs - last < cooldownMs) return false
        lastPunchMs = tMs
        return true
    }

    companion object {
        /** m/s², magnitude INCLUDING gravity. Starting value from CLAUDE.md; do not change without evidence. */
        const val PUNCH_THRESHOLD = 18f

        /** Minimum time between two punches. */
        const val PUNCH_COOLDOWN_MS = 500L
    }
}
