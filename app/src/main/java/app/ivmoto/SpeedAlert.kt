// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Optional speed warning: a beep and a buzz while a trip is recording and the speed is over the limit. */
object SpeedAlert {
    private const val PREFS = "ivmoto_speed_alert"
    private const val COOLDOWN_MS = 15_000L

    @Volatile private var lastAlertMs = 0L

    fun shouldAlert(enabled: Boolean, limitKmh: Int, speedKmh: Int, nowMs: Long, lastMs: Long): Boolean =
        enabled && speedKmh > limitKmh && nowMs - lastMs >= COOLDOWN_MS

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("enabled", on).apply()
    fun limit(ctx: Context): Int = prefs(ctx).getInt("limit", 100)
    fun setLimit(ctx: Context, kmh: Int) = prefs(ctx).edit().putInt("limit", kmh).apply()

    /** Called with every new speed reading of a recording trip. */
    fun check(ctx: Context, speedKmh: Int) {
        val now = System.currentTimeMillis()
        if (!shouldAlert(enabled(ctx), limit(ctx), speedKmh, now, lastAlertMs)) return
        lastAlertMs = now
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_BEEP2, 400) }
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
