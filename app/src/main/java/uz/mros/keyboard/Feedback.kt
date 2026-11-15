package uz.mros.keyboard

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Tebranish va tugma ovozi. */
class Feedback(context: Context, private val prefs: Prefs) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    private val audio: AudioManager? = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun key(type: KeyType) {
        if (prefs.vibrate) vibrate()
        if (prefs.sound) sound(type)
    }

    private fun vibrate() {
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            val strength = prefs.vibrateStrength
            val ms = longArrayOf(9L, 14L, 22L)[strength.coerceIn(0, 2)]
            val amp = intArrayOf(70, 150, 255)[strength.coerceIn(0, 2)]
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createOneShot(ms, amp)
            } else {
                VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            v.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    private fun sound(type: KeyType) {
        val a = audio ?: return
        val fx = when (type) {
            KeyType.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
            KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        try {
            a.playSoundEffect(fx, -1f)
        } catch (_: Exception) {
        }
    }
}
