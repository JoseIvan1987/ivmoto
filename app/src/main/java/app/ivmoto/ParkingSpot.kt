// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager

/** Where the bike was left: remembered when a live connection ends, shown on Home. */
object ParkingSpot {
    private const val PREFS = "ivmoto_parking"

    data class Spot(val lat: Double, val lon: Double, val time: Long)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(ctx: Context): Spot? {
        val p = prefs(ctx)
        if (!p.contains("lat")) return null
        return Spot(Double.fromBits(p.getLong("lat", 0)), Double.fromBits(p.getLong("lon", 0)), p.getLong("time", 0))
    }

    fun note(ctx: Context): String = prefs(ctx).getString("note", "").orEmpty()
    fun setNote(ctx: Context, text: String) = prefs(ctx).edit().putString("note", text.trim()).apply()

    fun photoFile(ctx: Context): java.io.File = java.io.File(ctx.filesDir, "parking.jpg")
    fun hasPhoto(ctx: Context): Boolean = photoFile(ctx).exists()

    fun clear(ctx: Context) {
        photoFile(ctx).delete()
        prefs(ctx).edit().clear().apply()
    }

    /** Called on every connection phase change; saves the position when a live ride/session ends. */
    fun onTransition(ctx: Context, old: Phase, new: Phase) {
        val wasLive = old == Phase.STREAMING || old == Phase.MIRRORING
        val ended = new == Phase.STOPPED || new == Phase.ERROR || new == Phase.WAITING_FOR_BIKE || new == Phase.IDLE
        if (wasLive && ended) saveHere(ctx)
    }

    private fun saveHere(ctx: Context) {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val best: Location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return
        photoFile(ctx).delete()
        prefs(ctx).edit()
            .remove("note")
            .putLong("lat", best.latitude.toBits())
            .putLong("lon", best.longitude.toBits())
            .putLong("time", System.currentTimeMillis())
            .apply()
        LogBus.log("→ parking spot saved")
    }
}
