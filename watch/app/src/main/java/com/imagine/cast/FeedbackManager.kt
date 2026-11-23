package com.imagine.cast

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Sound and haptics on the watch. Haptics and the red screen carry the feedback; the watch
 * speaker is weak, so sound is a bonus. Both sounds are loaded once, here.
 */
class FeedbackManager(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val armSound = soundPool.load(context, R.raw.arm, 1)
    private val fireballSound = soundPool.load(context, R.raw.fireball, 1)

    // minSdk is 30, and VibratorManager is API 31+, so the older accessor is kept as a fallback.
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** ARM_FIRE received live. */
    fun armed() {
        soundPool.play(armSound, 1f, 1f, 1, 0, 1f)
        buzz(ARM_BUZZ_MS, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    /** FIREBALL (Task 6). */
    fun fireball() {
        soundPool.play(fireballSound, 1f, 1f, 1, 0, 1f)
        buzz(FIREBALL_BUZZ_MS, MAX_AMPLITUDE)
    }

    fun release() {
        vibrator?.cancel()
        soundPool.release()
    }

    private fun buzz(ms: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val amp = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        v.vibrate(VibrationEffect.createOneShot(ms, amp))
    }

    private companion object {
        const val ARM_BUZZ_MS = 60L
        const val FIREBALL_BUZZ_MS = 150L
        const val MAX_AMPLITUDE = 255
    }
}
