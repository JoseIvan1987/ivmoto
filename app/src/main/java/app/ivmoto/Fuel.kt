// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One fill-up. [odometerKm] is optional; consumption needs it on at least two entries. */
data class FuelEntry(val id: String, val time: Long, val liters: Double, val price: Double, val odometerKm: Double?)

data class FuelSummary(val kmPerLiter: Double, val costPerKm: Double)

object FuelMath {
    /**
     * Km per litre and cost per km over the fills that have an odometer reading: for each fill, the km
     * since the previous one divided by the litres put in now. Null with fewer than two readings.
     */
    fun summary(entries: List<FuelEntry>): FuelSummary? {
        val withOdo = entries.filter { it.odometerKm != null }.sortedBy { it.odometerKm }
        if (withOdo.size < 2) return null
        var km = 0.0
        var liters = 0.0
        var cost = 0.0
        for (i in 1 until withOdo.size) {
            val leg = withOdo[i].odometerKm!! - withOdo[i - 1].odometerKm!!
            if (leg <= 0.0) continue
            km += leg
            liters += withOdo[i].liters
            cost += withOdo[i].price
        }
        if (km <= 0.0 || liters <= 0.0) return null
        return FuelSummary(km / liters, cost / km)
    }
}

object FuelStore {
    private const val PREFS = "ivmoto_fuel"
    private const val KEY = "entries"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(ctx: Context): List<FuelEntry> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FuelEntry(o.getString("id"), o.getLong("time"), o.getDouble("liters"), o.getDouble("price"),
                    if (o.has("odo")) o.getDouble("odo") else null)
            }
        }.getOrDefault(emptyList())
    }

    fun add(ctx: Context, liters: Double, price: Double, odometerKm: Double?) {
        val e = FuelEntry(System.currentTimeMillis().toString(), System.currentTimeMillis(), liters, price, odometerKm)
        write(ctx, list(ctx) + e)
    }

    fun remove(ctx: Context, id: String) {
        write(ctx, list(ctx).filterNot { it.id == id })
    }

    private fun write(ctx: Context, entries: List<FuelEntry>) {
        val arr = JSONArray()
        entries.forEach {
            val o = JSONObject().put("id", it.id).put("time", it.time).put("liters", it.liters).put("price", it.price)
            it.odometerKm?.let { odo -> o.put("odo", odo) }
            arr.put(o)
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}
