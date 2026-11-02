// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File

/** One ride: its route on the map, the numbers, GPX export and delete. */
class TripDetailActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var trip: Trip? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_trip_detail)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.td_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        findViewById<View>(R.id.td_back).setOnClickListener { finish() }

        val t = TripStore.get(this, intent.getStringExtra(EXTRA_ID).orEmpty())
        trip = t
        mapView = findViewById(R.id.td_map)
        mapView.onCreate(savedInstanceState)
        if (t == null) {
            finish()
            return
        }
        findViewById<TextView>(R.id.td_title).text = t.dateText()
        findViewById<TextView>(R.id.td_sub).text = t.timeRangeText()
        buildStats(t)
        findViewById<View>(R.id.td_export).setOnClickListener { exportGpx(t) }
        findViewById<View>(R.id.td_share).setOnClickListener { shareImage(t) }
        findViewById<SpeedChartView>(R.id.td_chart).values = SpeedSeries.resample(SpeedSeries.kmh(t.points), 80)
        findViewById<View>(R.id.td_delete).setOnClickListener { confirmDelete(t) }

        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/dark")) { st ->
                val pts = t.points.map { Point.fromLngLat(it.lon, it.lat) }
                if (pts.size >= 2) {
                    st.addSource(GeoJsonSource(SRC, FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(pts)))))
                    st.addLayer(
                        LineLayer(LAYER, SRC).withProperties(
                            lineColor("#38E1FF"), lineWidth(5f),
                            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                        ),
                    )
                    val b = LatLngBounds.Builder()
                    t.points.forEach { b.include(LatLng(it.lat, it.lon)) }
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 80))
                }
            }
        }
    }

    private fun buildStats(t: Trip) {
        val grid = findViewById<LinearLayout>(R.id.td_stats)
        val items = listOf(
            getString(R.string.td_distance) to t.distanceText(),
            getString(R.string.td_time) to t.durationText(),
            getString(R.string.td_avg) to "${t.avgKmh} km/h",
            getString(R.string.td_max) to "${t.maxKmh} km/h",
        )
        items.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(10) }
            }
            pair.forEachIndexed { i, (label, value) ->
                row.addView(
                    LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setBackgroundResource(R.drawable.bg_card)
                        setPadding(dp(8), dp(16), dp(8), dp(16))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            .apply { marginStart = if (i == 0) 0 else dp(6); marginEnd = if (i == 0) dp(6) else 0 }
                        addView(TextView(context).apply {
                            text = value
                            textSize = 20f
                            setTextColor(getColor(R.color.text_primary))
                            setTypeface(typeface, Typeface.BOLD)
                            gravity = Gravity.CENTER
                        })
                        addView(TextView(context).apply {
                            text = label
                            textSize = 12f
                            setTextColor(getColor(R.color.text_secondary))
                            gravity = Gravity.CENTER
                        })
                    },
                )
            }
            grid.addView(row)
        }
    }

    private fun exportGpx(t: Trip) {
        if (t.points.isEmpty()) {
            Toast.makeText(this, R.string.td_no_points, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val dir = File(cacheDir, "trip-export").apply { mkdirs() }
            val file = File(dir, TripGpx.suggestedFileName(t))
            file.writeText(TripGpx.toXml(t), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(contentResolver, file.name, uri)
            }
            startActivity(Intent.createChooser(send, getString(R.string.td_export)))
        }.onFailure { Toast.makeText(this, R.string.td_export_failed, Toast.LENGTH_LONG).show() }
    }

    private fun shareImage(t: Trip) {
        runCatching {
            val title = Nicknames.get(this, BikeMemory.selected(this)?.raw) ?: getString(R.string.app_name)
            val bmp = TripShareImage.render(this, t, title)
            val dir = File(cacheDir, "trip-export").apply { mkdirs() }
            val file = File(dir, "ivmoto-trip-${t.id}.png")
            file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(contentResolver, file.name, uri)
            }
            startActivity(Intent.createChooser(send, getString(R.string.td_share)))
        }.onFailure { Toast.makeText(this, R.string.td_export_failed, Toast.LENGTH_LONG).show() }
    }

    private fun confirmDelete(t: Trip) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.td_delete_ask)
            .setPositiveButton(R.string.td_delete) { _, _ ->
                TripStore.delete(this, t.id)
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }

    companion object {
        private const val EXTRA_ID = "trip_id"
        private const val SRC = "ivm-trip-src"
        private const val LAYER = "ivm-trip"

        fun start(ctx: Context, id: String) {
            ctx.startActivity(Intent(ctx, TripDetailActivity::class.java).putExtra(EXTRA_ID, id))
        }
    }
}
