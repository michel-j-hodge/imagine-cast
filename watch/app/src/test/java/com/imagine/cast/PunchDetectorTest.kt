package com.imagine.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunchDetectorTest {

    /** One sample every 20 ms, roughly SENSOR_DELAY_GAME. */
    private val stepMs = 20L

    /** Feeds (tMs, magnitude) samples, all along one axis, and returns the times that were punches. */
    private fun run(detector: PunchDetector, samples: List<Pair<Long, Float>>): List<Long> =
        samples.filter { (t, m) -> detector.onSample(0f, 0f, m, t) }.map { it.first }

    /** Resting wrist: gravity only (about 9.8 m/s²), from [fromMs] to [toMs]. */
    private fun rest(fromMs: Long, toMs: Long) = (fromMs until toMs step stepMs).map { it to 9.8f }

    /** A punch spike lasting about 80 ms, starting at [atMs], peaking at 30 m/s². */
    private fun spike(atMs: Long) = listOf(20f, 26f, 30f, 24f).mapIndexed { i, m -> atMs + i * stepMs to m }

    @Test
    fun belowThresholdIsNoPunch() {
        val d = PunchDetector()
        // Normal arm waving: well above rest, but under 18 m/s² including gravity.
        val samples = (0L until 2000L step stepMs).map { t -> t to (9.8f + 7f * ((t / stepMs) % 2)) }
        assertEquals(emptyList<Long>(), run(d, samples))
    }

    @Test
    fun exactlyAtThresholdIsNoPunch() {
        assertFalse(PunchDetector().onSample(0f, 0f, 18f, 0L))
    }

    @Test
    fun singleSpikeIsOnePunch() {
        val d = PunchDetector()
        val samples = rest(0, 200) + spike(200) + rest(280, 1000)
        assertEquals(listOf(200L), run(d, samples))
    }

    @Test
    fun twoSpikes300msApartIsOnePunch() {
        val d = PunchDetector()
        val samples = rest(0, 200) + spike(200) + rest(280, 500) + spike(500) + rest(580, 1200)
        assertEquals(listOf(200L), run(d, samples))
    }

    @Test
    fun twoSpikes600msApartIsTwoPunches() {
        val d = PunchDetector()
        val samples = rest(0, 200) + spike(200) + rest(280, 800) + spike(800) + rest(880, 1500)
        assertEquals(listOf(200L, 800L), run(d, samples))
    }

    @Test
    fun magnitudeUsesAllThreeAxes() {
        // 12, 12, 6 → magnitude 18.0 (not a punch); 12, 12, 7 → about 18.4 (a punch).
        assertFalse(PunchDetector().onSample(12f, 12f, 6f, 0L))
        assertTrue(PunchDetector().onSample(12f, 12f, 7f, 0L))
    }

    @Test
    fun clockIsUsedWhenNoTimeIsGiven() {
        var now = 1000L
        val d = PunchDetector(clock = { now })
        assertTrue(d.onSample(0f, 0f, 30f))
        now = 1300L
        assertFalse(d.onSample(0f, 0f, 30f))
        now = 1600L
        assertTrue(d.onSample(0f, 0f, 30f))
    }
}
