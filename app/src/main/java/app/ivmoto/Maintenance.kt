// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A service item (oil, chain…). [name] is null for the built-in ones, whose name comes from resources. */
data class MaintTask(val id: String, val name: String?, val intervalKm: Int, val lastDoneKm: Int)

enum class MaintLevel { OK, SOON, DUE }

object Maintenance {
    fun remainingKm(t: MaintTask, currentKm: Int): Int = t.lastDoneKm + t.intervalKm - currentKm

    /** DUE at or past the interval; SOON within 10 % of it (never less than 100 km); otherwise OK. */
    fun level(t: MaintTask, currentKm: Int): MaintLevel {
        val left = remainingKm(t, currentKm)
        return when {
            left <= 0 -> MaintLevel.DUE
            left <= maxOf(100, t.intervalKm / 10) -> MaintLevel.SOON
            else -> MaintLevel.OK
        }
    }
}

/** The bike's kilometres: what the rider typed once, plus every km IVMoto has recorded since. */
object Odometer {
    private const val PREFS = "ivmoto_odometer"

    fun compute(baseKm: Double, baseTripKm: Double, tripsKm: Double): Double =
        baseKm + maxOf(0.0, tripsKm - baseTripKm)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun tripsKm(ctx: Context): Double = TripStore.list(ctx).sumOf { it.distanceKm }

    fun currentKm(ctx: Context): Int {
        val p = prefs(ctx)
        return compute(
            p.getFloat("base_km", 0f).toDouble(),
            p.getFloat("base_trip_km", 0f).toDouble(),
            tripsKm(ctx),
        ).toInt()
    }

    fun set(ctx: Context, km: Int) {
        prefs(ctx).edit()
            .putFloat("base_km", km.toFloat())
            .putFloat("base_trip_km", tripsKm(ctx).toFloat())
            .apply()
    }
}

object MaintenanceStore {
    private const val PREFS = "ivmoto_maintenance"
    private const val KEY = "tasks"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Generic intervals; the rider should follow the owner's manual for the exact numbers. */
    private fun defaults(startKm: Int) = listOf(
        MaintTask("oil", null, 5000, startKm),
        MaintTask("chain", null, 500, startKm),
        MaintTask("tires", null, 2000, startKm),
        MaintTask("brakes", null, 10000, startKm),
        MaintTask("airfilter", null, 10000, startKm),
    )

    fun list(ctx: Context): List<MaintTask> {
        val raw = prefs(ctx).getString(KEY, null)
        if (raw == null) {
            val d = defaults(Odometer.currentKm(ctx))
            save(ctx, d)
            return d
        }
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MaintTask(o.getString("id"), o.optString("name").takeIf { it.isNotBlank() }, o.getInt("interval"), o.getInt("last"))
            }
        }.getOrDefault(emptyList())
    }

    fun save(ctx: Context, tasks: List<MaintTask>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name ?: "").put("interval", it.intervalKm).put("last", it.lastDoneKm)) }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun markDone(ctx: Context, id: String) {
        val now = Odometer.currentKm(ctx)
        save(ctx, list(ctx).map { if (it.id == id) it.copy(lastDoneKm = now) else it })
    }

    fun setInterval(ctx: Context, id: String, km: Int) {
        save(ctx, list(ctx).map { if (it.id == id) it.copy(intervalKm = km.coerceAtLeast(1)) else it })
    }

    fun add(ctx: Context, name: String, intervalKm: Int) {
        val now = Odometer.currentKm(ctx)
        save(ctx, list(ctx) + MaintTask("c" + System.currentTimeMillis(), name.trim(), intervalKm.coerceAtLeast(1), now))
    }

    fun remove(ctx: Context, id: String) {
        save(ctx, list(ctx).filterNot { it.id == id })
    }

    /** The task that is overdue or closest to due, or null when everything is fine. */
    fun mostUrgent(ctx: Context): Pair<MaintTask, Int>? {
        val now = Odometer.currentKm(ctx)
        return list(ctx)
            .filter { Maintenance.level(it, now) != MaintLevel.OK }
            .map { it to Maintenance.remainingKm(it, now) }
            .minByOrNull { it.second }
    }
}
