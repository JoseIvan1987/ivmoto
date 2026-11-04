// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The rider's choices for the on-the-road safety helpers. */
object RideSettings {
    private const val PREFS = "ivmoto_ride"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun restEnabled(ctx: Context) = prefs(ctx).getBoolean("rest", true)
    fun setRestEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("rest", on).apply()
    fun restHours(ctx: Context) = prefs(ctx).getInt("rest_hours", 2)
    fun setRestHours(ctx: Context, h: Int) = prefs(ctx).edit().putInt("rest_hours", h).apply()

    fun roadLimit(ctx: Context) = prefs(ctx).getBoolean("road_limit", false)
    fun setRoadLimit(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("road_limit", on).apply()

    fun curves(ctx: Context) = prefs(ctx).getBoolean("curves", true)
    fun setCurves(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("curves", on).apply()

    fun avoidDirt(ctx: Context) = prefs(ctx).getBoolean("avoid_dirt", false)
    fun setAvoidDirt(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("avoid_dirt", on).apply()

    fun tankLiters(ctx: Context): Double = prefs(ctx).getFloat("tank", 0f).toDouble()
    fun setTankLiters(ctx: Context, l: Double) = prefs(ctx).edit().putFloat("tank", l.toFloat()).apply()
}

/** Things that happen while a ride is recording: the break reminder and the road-limit warning. */
object RideAlerts {
    private const val CHANNEL = "ride_rest"
    private const val REST_ID = 21
    @Volatile private var lastRestAtMs = 0L
    @Volatile private var lastLimitFetchMs = 0L
    @Volatile private var roadLimitKmh: Int? = null

    /** Call on every new fix of a recording trip. */
    fun onFix(ctx: Context, movingMs: Long, speedKmh: Int, lat: Double, lon: Double) {
        if (movingMs < lastRestAtMs) lastRestAtMs = 0L // a new trip started
        if (RideSettings.restEnabled(ctx)) {
            val interval = RideSettings.restHours(ctx) * 3_600_000L
            if (RestReminder.due(movingMs, lastRestAtMs, interval)) {
                lastRestAtMs = movingMs
                notifyRest(ctx, movingMs)
            }
        }
        if (RideSettings.roadLimit(ctx)) {
            val now = System.currentTimeMillis()
            if (now - lastLimitFetchMs > 12_000L) {
                lastLimitFetchMs = now
                OverpassClient.maxspeedAsync(lat, lon) { roadLimitKmh = it.kmh }
            }
            roadLimitKmh?.let { SpeedAlert.checkAgainst(ctx, it + 5, speedKmh) }
        }
    }

    private fun notifyRest(ctx: Context, movingMs: Long) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.st_rest), NotificationManager.IMPORTANCE_HIGH))
        }
        val hours = movingMs / 3_600_000L
        val mins = movingMs / 60_000L % 60
        val text = ctx.getString(R.string.rest_text, if (mins == 0L) "$hours h" else "$hours h $mins min")
        val n = Notification.Builder(ctx, CHANNEL)
            .setContentTitle(ctx.getString(R.string.rest_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        try { nm.notify(REST_ID, n) } catch (_: SecurityException) {}
        buzz(ctx, 600)
    }

    /** After a ride: tell the rider when a service has come due (once per task and level). */
    fun maintenanceCheck(ctx: Context) {
        val (task, left) = MaintenanceStore.mostUrgent(ctx) ?: return
        val level = Maintenance.level(task, Odometer.currentKm(ctx))
        val key = task.id + ":" + level
        val prefs = ctx.getSharedPreferences("ivmoto_ride", Context.MODE_PRIVATE)
        if (prefs.getString("maint_notified", "") == key) return
        prefs.edit().putString("maint_notified", key).apply()
        val name = task.name ?: ctx.getString(
            when (task.id) {
                "oil" -> R.string.maint_oil
                "chain" -> R.string.maint_chain
                "tires" -> R.string.maint_tires
                "brakes" -> R.string.maint_brakes
                else -> R.string.maint_air
            },
        )
        val km = java.lang.String.format(java.util.Locale.getDefault(), "%,d", kotlin.math.abs(left))
        val text = name + ": " + if (left <= 0) ctx.getString(R.string.maint_overdue, km) else ctx.getString(R.string.maint_left, km)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel("ride_maint", ctx.getString(R.string.home_tile_maint), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val n = Notification.Builder(ctx, "ride_maint")
            .setContentTitle(ctx.getString(R.string.maint_notify_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        try { nm.notify(22, n) } catch (_: SecurityException) {}
    }

    fun buzz(ctx: Context, ms: Long) {
        runCatching {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
