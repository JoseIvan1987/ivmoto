// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import org.maplibre.android.MapLibre

/** Process entry — installs crash capture before any Activity runs. */
class IVMotoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            LogBus.includeSecrets = AppSettings.includeSecretsInLogs(this)
        } catch (_: Exception) {
        }
        CrashGuard.install(this)
        CrashGuard.hydrateLogBus(this)
        try {
            AppSettings.applyToHolder(this)
        } catch (_: Exception) {
        }
        // After hydrate so Share Logs still show prior crash, then stamp this process build.
        LogBus.logSessionBanner()
        AppHttp.init(this)
        ConnectionState.onChange = { old, new -> ParkingSpot.onTransition(this, old, new) }
        Shortcuts.publish(this)
        registerTypeOnPhone()
        try {
            MapLibre.getInstance(this)
            // After getInstance only — AppHttp network callbacks must not set the client first.
            AppHttp.onMapLibreReady()
        } catch (e: Exception) {
            android.util.Log.w("IVMoto", "MapLibre init failed: $e")
        }
    }

    private var startedActivities = 0

    /** The dash has no keyboard: its magnifier opens the typing screen on the phone. */
    private fun registerTypeOnPhone() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { startedActivities++ }
            override fun onActivityStopped(a: Activity) { startedActivities-- }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        DashRemote.setTypeOnPhoneHandler {
            if (startedActivities > 0) DashSearchActivity.open(this) else notifyTypeOnPhone()
        }
    }

    /** Android blocks opening screens from the background, so ask with a notification instead. */
    private fun notifyTypeOnPhone() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = "dash_search"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channel, getString(R.string.notif_dash_search_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val open = Intent(this, DashSearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 7, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = android.app.Notification.Builder(this, channel)
            .setContentTitle(getString(R.string.dsa_title))
            .setContentText(getString(R.string.notif_dash_search_text))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { nm.notify(7, n) } catch (_: SecurityException) {}
    }
}
