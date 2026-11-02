// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import kotlin.math.cos

/** How many map tiles an area needs, so a download can be sized before it starts. */
object OfflineEstimate {
    const val MAX_TILES = 25_000
    private const val AVG_TILE_BYTES = 30_000L

    private fun tileX(lon: Double, z: Int) = floor((lon + 180.0) / 360.0 * 2.0.pow(z)).toInt()

    private fun tileY(lat: Double, z: Int): Int {
        val rad = Math.toRadians(lat.coerceIn(-85.05, 85.05))
        return floor((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * 2.0.pow(z)).toInt()
    }

    fun tileCount(north: Double, south: Double, east: Double, west: Double, minZoom: Int, maxZoom: Int): Int {
        var total = 0L
        for (z in minZoom..maxZoom) {
            val x = tileX(east, z) - tileX(west, z) + 1
            val y = tileY(south, z) - tileY(north, z) + 1
            total += maxOf(1, x).toLong() * maxOf(1, y)
        }
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun megabytes(tiles: Int): Int = maxOf(1L, tiles * AVG_TILE_BYTES / 1_000_000L).toInt()
}

/** The areas the rider downloaded for use without internet. */
class OfflineMapsActivity : ListScreenActivity() {
    override val screenTitle = R.string.off_title

    private var sizes: Map<String, Long> = emptyMap()

    override fun onResume() {
        super.onResume()
        MapOfflineManager.listAreas(this) { found ->
            runOnUiThread {
                sizes = found.associate { it.name to it.sizeBytes }
                rebuild()
            }
        }
    }

    override fun build() {
        card(getString(R.string.off_info_title), getString(R.string.off_info_desc), R.color.ivm_cyan)
        OfflineAreasStore.list(this).sortedByDescending { it.createdAt }.forEach { area ->
            val size = sizes[area.name]?.let { String.format(java.util.Locale.getDefault(), "%.0f MB", it / 1_000_000.0) } ?: "…"
            card(area.name, getString(R.string.off_area_desc, area.zoomMax, size)).setOnClickListener { confirmDelete(area.name) }
        }
    }

    private fun confirmDelete(name: String) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.off_delete_ask, name))
            .setPositiveButton(R.string.maint_remove) { _, _ ->
                MapOfflineManager.deleteArea(this, name) {
                    OfflineAreasStore.remove(this, name)
                    runOnUiThread { rebuild() }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, OfflineMapsActivity::class.java))
        }
    }
}
