// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A place the rider saved (home, work, a favourite road stop…). */
data class Place(val id: String, val name: String, val lat: Double, val lon: Double)

/** Saved places live on the phone only, as JSON in private preferences. */
object PlacesStore {
    private const val PREFS = "ivmoto_places"
    private const val KEY = "list"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Place> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Place(o.getString("id"), o.getString("name"), o.getDouble("lat"), o.getDouble("lon"))
            }
        }.getOrDefault(emptyList())
    }

    fun add(ctx: Context, name: String, lat: Double, lon: Double): Place {
        val place = Place(System.currentTimeMillis().toString(), name.trim().ifEmpty { "Lugar" }, lat, lon)
        write(ctx, list(ctx) + place)
        return place
    }

    fun rename(ctx: Context, id: String, name: String) {
        write(ctx, list(ctx).map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it })
    }

    fun remove(ctx: Context, id: String) {
        write(ctx, list(ctx).filterNot { it.id == id })
    }

    private fun write(ctx: Context, places: List<Place>) {
        val arr = JSONArray()
        places.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("lat", it.lat).put("lon", it.lon))
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}
