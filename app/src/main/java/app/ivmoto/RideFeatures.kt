// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** "Take a break": a nudge after a stretch of riding. */
object RestReminder {
    fun due(movingMs: Long, lastReminderAtMs: Long, intervalMs: Long): Boolean =
        intervalMs > 0 && movingMs - lastReminderAtMs >= intervalMs
}

data class Curve(val distanceM: Double, val turnDeg: Double)

/** Finds a sharp bend on the planned route a little way ahead. */
object CurveAhead {
    private const val STEP_M = 20.0
    private const val WINDOW = 3 // samples on each side of the bend (60 m)

    fun find(points: List<GpxPoint>, from: Int, aheadM: Double = 300.0, minTurnDeg: Double = 60.0): Curve? {
        if (points.size - from < 3) return null
        val s = resample(points, from.coerceAtLeast(0), aheadM + 2 * WINDOW * STEP_M)
        var k = 0
        while (k + 2 * WINDOW < s.size) {
            val dist = (k + WINDOW) * STEP_M
            if (dist > aheadM) break
            val b1 = bearing(s[k], s[k + WINDOW])
            val b2 = bearing(s[k + WINDOW], s[k + 2 * WINDOW])
            val turn = angleDiff(b1, b2)
            if (turn >= minTurnDeg) return Curve(dist, turn)
            k++
        }
        return null
    }

    private fun resample(points: List<GpxPoint>, from: Int, totalM: Double): List<GpxPoint> {
        val out = ArrayList<GpxPoint>()
        out.add(points[from])
        var carry = 0.0
        var walked = 0.0
        var i = from
        while (i + 1 < points.size && walked < totalM) {
            val a = points[i]
            val b = points[i + 1]
            val seg = metres(a, b)
            if (seg <= 0.0) { i++; continue }
            var pos = STEP_M - carry
            while (pos <= seg) {
                val t = pos / seg
                out.add(GpxPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t))
                pos += STEP_M
            }
            carry = seg - (pos - STEP_M)
            walked += seg
            i++
        }
        return out
    }

    private fun metres(a: GpxPoint, b: GpxPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun bearing(a: GpxPoint, b: GpxPoint): Double {
        val y = sin(Math.toRadians(b.lon - a.lon)) * cos(Math.toRadians(b.lat))
        val x = cos(Math.toRadians(a.lat)) * sin(Math.toRadians(b.lat)) -
            sin(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * cos(Math.toRadians(b.lon - a.lon))
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun angleDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }
}

/** How far the fuel in the tank should still take the bike (assumes the last fill was a full tank). */
object FuelRange {
    fun remainingKm(tankLiters: Double, kmPerLiter: Double, kmSinceFill: Double): Double? {
        if (tankLiters <= 0.0 || kmPerLiter <= 0.0) return null
        return max(0.0, tankLiters * kmPerLiter - kmSinceFill)
    }
}

/** A service done on the bike, with what it cost. */
data class MaintLogEntry(val taskId: String, val name: String, val km: Int, val cost: Double, val time: Long)

object MaintLogStore {
    private const val PREFS = "ivmoto_maint_log"
    private const val KEY = "entries"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(ctx: Context): List<MaintLogEntry> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MaintLogEntry(o.getString("id"), o.getString("name"), o.getInt("km"), o.getDouble("cost"), o.getLong("time"))
            }
        }.getOrDefault(emptyList())
    }

    fun add(ctx: Context, e: MaintLogEntry) {
        val arr = JSONArray()
        (list(ctx) + e).forEach {
            arr.put(JSONObject().put("id", it.taskId).put("name", it.name).put("km", it.km).put("cost", it.cost).put("time", it.time))
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}

data class ExpenseSummary(val fuelTotal: Double, val serviceTotal: Double, val total: Double, val costPerKm: Double?)

object Expenses {
    fun summarize(fuel: List<FuelEntry>, service: List<MaintLogEntry>): ExpenseSummary {
        val fuelTotal = fuel.sumOf { it.price }
        val serviceTotal = service.sumOf { it.cost }
        val readings = fuel.mapNotNull { it.odometerKm } + service.map { it.km.toDouble() }
        val span = if (readings.size >= 2) readings.max() - readings.min() else 0.0
        val total = fuelTotal + serviceTotal
        return ExpenseSummary(fuelTotal, serviceTotal, total, if (span > 0.0) total / span else null)
    }
}

data class HourForecast(val rainPct: Int, val windKmh: Double, val gustKmh: Double)
data class PointForecast(val hours: List<HourForecast>)
data class WeatherSummary(val maxRainPct: Int, val maxWindKmh: Double, val maxGustKmh: Double) {
    val warnRain: Boolean get() = maxRainPct >= 60
    val warnWind: Boolean get() = maxGustKmh >= 50.0 || maxWindKmh >= 40.0
}

/** Rain and wind along the route from Open-Meteo (free, no key, no account). */
object RouteWeather {
    fun parse(json: String): List<PointForecast> {
        val root = JSONArray(if (json.trimStart().startsWith("[")) json else "[$json]")
        return (0 until root.length()).map { i ->
            val h = root.getJSONObject(i).getJSONObject("hourly")
            val rain = h.getJSONArray("precipitation_probability")
            val wind = h.getJSONArray("wind_speed_10m")
            val gust = h.getJSONArray("wind_gusts_10m")
            PointForecast((0 until rain.length()).map { k ->
                HourForecast(rain.optInt(k, 0), wind.optDouble(k, 0.0), gust.optDouble(k, 0.0))
            })
        }
    }

    /** For each point, the forecast of the hour the rider is expected to get there. */
    fun summarize(points: List<PointForecast>, etaHoursPerPoint: List<Int>): WeatherSummary {
        var rain = 0
        var wind = 0.0
        var gust = 0.0
        points.forEachIndexed { i, p ->
            if (p.hours.isEmpty()) return@forEachIndexed
            val idx = (etaHoursPerPoint.getOrElse(i) { 0 }).coerceIn(0, p.hours.size - 1)
            val h = p.hours[idx]
            rain = max(rain, h.rainPct)
            wind = max(wind, h.windKmh)
            gust = max(gust, h.gustKmh)
        }
        return WeatherSummary(rain, wind, gust)
    }

    /** Blocking: call from a background thread. [lats]/[lons] are sample points along the route. */
    fun fetch(lats: List<Double>, lons: List<Double>): List<PointForecast> {
        val la = lats.joinToString(",") { fmt(it) }
        val lo = lons.joinToString(",") { fmt(it) }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$la&longitude=$lo" +
            "&hourly=precipitation_probability,wind_speed_10m,wind_gusts_10m&forecast_hours=12&timezone=auto"
        val conn = AppHttp.openUrl(url).apply { connectTimeout = 10_000; readTimeout = 10_000 }
        return try {
            if (conn.responseCode != 200) emptyList()
            else parse(conn.inputStream.use { UpdateRules.readLimited(it, 256 * 1024) })
        } finally {
            conn.disconnect()
        }
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.3f", v)
}

/** The text for "share where I am". */
object LiveShare {
    fun message(lat: Double, lon: Double, name: String, accuracyM: Float?): String {
        fun c(v: Double) = String.format(Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
        val acc = accuracyM?.let { " (±${it.toInt()} m)" }.orEmpty()
        return "📍 $name · ubicación$acc\nhttps://maps.google.com/?q=${c(lat)},${c(lon)}\nEnviado desde IVMoto"
    }

    fun share(ctx: Context, lat: Double, lon: Double, accuracyM: Float?) {
        val name = Nicknames.get(ctx, BikeMemory.selected(ctx)?.raw) ?: ctx.getString(R.string.app_name)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, message(lat, lon, name, accuracyM))
        }
        ctx.startActivity(
            android.content.Intent.createChooser(send, ctx.getString(R.string.share_location))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
