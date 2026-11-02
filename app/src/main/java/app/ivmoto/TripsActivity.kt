// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** My rides: totals on top, then one card per recorded trip (newest first). */
class TripsActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        findViewById<TextView>(R.id.settings_title).setText(R.string.home_tile_trips)
        findViewById<View>(R.id.settings_back).setOnClickListener { finish() }
        list = findViewById(R.id.settings_list)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        list.removeAllViews()
        val trips = TripStore.list(this).sortedByDescending { it.start }
        if (trips.isEmpty()) {
            list.addView(card().apply {
                addView(text(getString(R.string.trips_empty), 16f, R.color.text_primary, true))
                addView(text(getString(R.string.trips_empty_desc), 13f, R.color.text_secondary, false))
            })
            return
        }
        // Totals
        val km = trips.sumOf { it.distanceKm }
        val ms = trips.sumOf { it.movingTimeMs }
        val totals = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) }
        }
        listOf(
            "${trips.size}" to getString(R.string.trips_total_rides),
            "%.0f km".format(km) to getString(R.string.trips_total_km),
            "%d h".format(ms / 3_600_000) to getString(R.string.trips_total_time),
        ).forEachIndexed { i, (value, label) ->
            totals.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_card)
                    setPadding(dp(8), dp(14), dp(8), dp(14))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { marginStart = if (i == 0) 0 else dp(6); marginEnd = if (i == 2) 0 else dp(6) }
                    addView(text(value, 20f, R.color.text_primary, true).apply { gravity = Gravity.CENTER })
                    addView(text(label, 11f, R.color.text_secondary, false).apply { gravity = Gravity.CENTER })
                },
            )
        }
        list.addView(totals)

        trips.forEach { trip -> list.addView(tripCard(trip)) }
    }

    private fun tripCard(trip: Trip): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_tile)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) }
            setOnClickListener { TripDetailActivity.start(this@TripsActivity, trip.id) }
        }
        val w = dp(92)
        val h = dp(68)
        val thumb = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(w, h).apply { marginEnd = dp(14) }
            setImageBitmap(
                TripRouteThumb.render(
                    trip, w, h,
                    getColor(R.color.ivm_cyan), getColor(R.color.surface_high),
                    getColor(R.color.status_live), getColor(R.color.status_error),
                ),
            )
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(trip.dateText(), 15f, R.color.text_primary, true))
        col.addView(text("${trip.distanceText()} · ${trip.durationText()}", 13f, R.color.text_secondary, false))
        col.addView(text(getString(R.string.trips_max_speed, trip.maxKmh), 12f, R.color.ivm_cyan, false))
        row.addView(thumb)
        row.addView(col)
        return row
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_tile)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10) }
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(getColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, TripsActivity::class.java))
        }
    }
}
