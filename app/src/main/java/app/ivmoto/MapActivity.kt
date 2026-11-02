// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

/** IVMoto's map: see where you are, search a place and preview the road route to it. */
class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var here: Location? = null
    private var centeredOnce = false

    private lateinit var search: EditText
    private lateinit var results: LinearLayout
    private lateinit var sheet: View
    private lateinit var sheetTitle: TextView
    private lateinit var sheetInfo: TextView
    private var destination: MapPlace? = null
    private var pendingDest: MapPlace? = null
    private lateinit var tripButton: TextView
    private val tick = android.os.Handler(android.os.Looper.getMainLooper())
    private val tickRun = object : Runnable {
        override fun run() {
            renderTrip()
            tick.postDelayed(this, 1_000)
        }
    }

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startLocation()
    }

    private val listener = LocationListener { loc ->
        here = loc
        pushMarkers()
        maybeCenter()
    }

    private fun maybeCenter() {
        val m = map ?: return
        val loc = here ?: return
        if (centeredOnce || pendingDest != null) return
        centeredOnce = true
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 14.5))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_map)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.map_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        search = findViewById(R.id.map_search)
        results = findViewById(R.id.map_results)
        sheet = findViewById(R.id.map_sheet)
        sheetTitle = findViewById(R.id.map_sheet_title)
        sheetInfo = findViewById(R.id.map_sheet_info)
        findViewById<View>(R.id.map_back).setOnClickListener { finish() }
        findViewById<View>(R.id.map_sheet_save).setOnClickListener { saveDestination() }
        findViewById<View>(R.id.map_offline).setOnClickListener { offerOfflineDownload() }
        findViewById<View>(R.id.map_sheet_dash).setOnClickListener {
            destination?.let { ConnectActivity.startDash(this, it) }
        }
        tripButton = findViewById(R.id.map_trip)
        tripButton.setOnClickListener { toggleTrip() }
        findViewById<View>(R.id.map_sheet_close).setOnClickListener { clearDestination() }
        findViewById<View>(R.id.map_locate).setOnClickListener {
            val h = here
            if (h != null) {
                centerOn(h.latitude, h.longitude)
            } else {
                Toast.makeText(this, R.string.map_waiting_location, Toast.LENGTH_SHORT).show()
                ensureLocation()
            }
        }
        search.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                runSearch()
                true
            } else {
                false
            }
        }

        if (intent.hasExtra(EXTRA_LAT)) {
            pendingDest = MapPlace(intent.getStringExtra(EXTRA_NAME).orEmpty(), intent.getDoubleExtra(EXTRA_LAT, 0.0), intent.getDoubleExtra(EXTRA_LON, 0.0))
        }
        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.addOnDidFailLoadingMapListener { msg ->
            LogBus.log("[MAP] failed to load: $msg")
            runOnUiThread { Toast.makeText(this, R.string.map_load_failed, Toast.LENGTH_LONG).show() }
        }
        mapView.getMapAsync { m ->
            map = m
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(DEFAULT_LAT, DEFAULT_LON), DEFAULT_ZOOM))
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { st ->
                style = st
                st.addSource(GeoJsonSource(SRC_ROUTE))
                st.addSource(GeoJsonSource(SRC_POINTS))
                st.addLayer(
                    LineLayer(LAYER_ROUTE, SRC_ROUTE).withProperties(
                        lineColor("#38E1FF"), lineWidth(6f),
                        lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                st.addLayer(
                    CircleLayer(LAYER_POINTS, SRC_POINTS).withProperties(
                        circleRadius(8f), circleColor("#2F8CFF"),
                        circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
                    ),
                )
                pushMarkers()
                LogBus.log("[MAP] style loaded")
                maybeCenter()
                pendingDest?.let { pendingDest = null; choose(it) }
            }
        }
        ensureLocation()
    }

    // ---- location -------------------------------------------------------------------------

    private fun ensureLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startLocation()
        } else {
            askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            Toast.makeText(this, R.string.map_location_off, Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
                ?.let { listener.onLocationChanged(it) }
            providers.forEach { lm.requestLocationUpdates(it, 2_000L, 5f, listener) }
        }
    }

    private fun centerOn(lat: Double, lon: Double) {
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(lat, lon)).zoom(14.5).build(),
        ))
    }

    // ---- search + route -------------------------------------------------------------------

    private fun runSearch() {
        val q = search.text.toString().trim()
        if (q.length < 2) return
        results.removeAllViews()
        NominatimSearch.searchAsync(
            query = q,
            nearLat = here?.latitude,
            nearLon = here?.longitude,
            onResult = { places -> runOnUiThread { showResults(places.take(6)) } },
            onError = { runOnUiThread { Toast.makeText(this, R.string.map_search_failed, Toast.LENGTH_SHORT).show() } },
        )
    }

    private fun showResults(places: List<MapPlace>) {
        results.removeAllViews()
        if (places.isEmpty()) {
            results.addView(row(getString(R.string.map_no_results), "") {})
            return
        }
        places.forEach { p -> results.addView(row(p.name, p.subtitle) { choose(p) }) }
    }

    private fun row(title: String, sub: String, onClick: () -> Unit): View {
        val v = layoutInflater.inflate(R.layout.row_map_result, results, false)
        v.findViewById<TextView>(R.id.row_title).text = title
        v.findViewById<TextView>(R.id.row_sub).text = sub
        v.setOnClickListener { onClick() }
        return v
    }

    private fun choose(place: MapPlace) {
        results.removeAllViews()
        destination = place
        sheet.visibility = View.VISIBLE
        sheetTitle.text = place.name
        sheetInfo.setText(R.string.map_calculating)
        val from = here
        pushMarkers()
        if (from == null) {
            sheetInfo.setText(R.string.map_no_gps)
            centerOn(place.lat, place.lon)
            return
        }
        OsrmRouter.routeAsync(
            from.latitude, from.longitude, place.lat, place.lon,
            onResult = { route -> runOnUiThread { showRoute(route) } },
            onError = { runOnUiThread { sheetInfo.setText(R.string.map_route_failed) } },
        )
    }

    private fun showRoute(route: OsrmRouter.Route) {
        val km = route.distanceM / 1000.0
        val min = (route.durationSec / 60.0).roundToInt()
        sheetInfo.text = getString(R.string.map_route_info, "%.1f".format(km), formatMinutes(min))
        val pts = route.points.map { Point.fromLngLat(it.lon, it.lat) }
        (style?.getSource(SRC_ROUTE) as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(pts))))
        if (route.points.size >= 2) {
            val b = LatLngBounds.Builder()
            route.points.forEach { b.include(LatLng(it.lat, it.lon)) }
            map?.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120))
        }
    }

    private fun formatMinutes(min: Int): String =
        if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"

    private fun clearDestination() {
        destination = null
        sheet.visibility = View.GONE
        (style?.getSource(SRC_ROUTE) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        pushMarkers()
    }

    private fun pushMarkers() {
        val features = mutableListOf<Feature>()
        here?.let { features.add(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))) }
        destination?.let { features.add(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) }
        (style?.getSource(SRC_POINTS) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun saveDestination() {
        val d = destination ?: return
        val input = EditText(this).apply {
            setText(d.name)
            setSelection(text.length)
            hint = getString(R.string.map_save_name)
            setPadding(48, 32, 48, 16)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.map_save)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                PlacesStore.add(this, input.text.toString(), d.lat, d.lon)
                Toast.makeText(this, R.string.map_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- offline download -----------------------------------------------------------------

    private fun offerOfflineDownload() {
        val m = map ?: return
        val b = m.projection.visibleRegion.latLngBounds
        val options = listOf(13 to R.string.off_city, 15 to R.string.off_street)
        val counts = options.map { (z, _) ->
            OfflineEstimate.tileCount(b.latitudeNorth, b.latitudeSouth, b.longitudeEast, b.longitudeWest, MIN_OFFLINE_ZOOM, z)
        }
        val labels = options.mapIndexed { i, (_, res) -> getString(res, OfflineEstimate.megabytes(counts[i])) }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.off_download)
            .setItems(labels.toTypedArray()) { _, i ->
                if (counts[i] > OfflineEstimate.MAX_TILES) {
                    Toast.makeText(this, R.string.off_too_big, Toast.LENGTH_LONG).show()
                } else {
                    askAreaName(b, options[i].first)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun askAreaName(bounds: LatLngBounds, maxZoom: Int) {
        val default = getString(R.string.off_default_name, OfflineAreasStore.list(this).size + 1)
        FormDialog.show(this, getString(R.string.off_download), listOf(FormDialog.Field(getString(R.string.off_name_hint), default))) { v ->
            startOfflineDownload(v[0].ifBlank { default }, bounds, maxZoom)
        }
    }

    private fun startOfflineDownload(name: String, bounds: LatLngBounds, maxZoom: Int) {
        val button = findViewById<TextView>(R.id.map_offline)
        Toast.makeText(this, R.string.off_progress, Toast.LENGTH_SHORT).show()
        MapOfflineManager.downloadArea(
            this, name, bounds, MIN_OFFLINE_ZOOM.toDouble(), maxZoom.toDouble(), listOf(STYLE_URL to true),
            onProgress = { percent, _ -> runOnUiThread { button.text = "$percent%" } },
            onDone = { ok, message ->
                runOnUiThread {
                    button.text = "↓"
                    if (ok) {
                        OfflineAreasStore.add(
                            this,
                            OfflineAreasStore.Area(
                                name, bounds.latitudeNorth, bounds.latitudeSouth, bounds.longitudeEast, bounds.longitudeWest,
                                maxZoom, vector = true, raster = false, createdAt = System.currentTimeMillis(),
                            ),
                        )
                        Toast.makeText(this, R.string.off_done, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, getString(R.string.off_failed, message), Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    // ---- trips ----------------------------------------------------------------------------

    private fun toggleTrip() {
        val rec = TripLogger.get(this)
        if (rec.recording) {
            rec.stopAndSave()
            Toast.makeText(this, R.string.mtrip_saved, Toast.LENGTH_SHORT).show()
        } else if (!rec.start()) {
            Toast.makeText(this, R.string.mtrip_need_gps, Toast.LENGTH_LONG).show()
            ensureLocation()
        }
        renderTrip()
    }

    private fun renderTrip() {
        val s = TripLogger.get(this).snapshot()
        if (!s.recording) {
            tripButton.setText(R.string.mtrip_start)
            return
        }
        val secs = s.movingTimeMs / 1000
        val time = "%d:%02d".format(secs / 60, secs % 60)
        tripButton.text = getString(R.string.mtrip_live, "%.1f".format(s.distanceMeters / 1000.0), time)
    }

    // ---- MapView lifecycle ----------------------------------------------------------------

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume(); tick.post(tickRun) }
    override fun onPause() { tick.removeCallbacks(tickRun); mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    override fun onDestroy() {
        runCatching { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(listener) }
        mapView.onDestroy()
        super.onDestroy()
    }

    companion object {
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
        private const val MIN_OFFLINE_ZOOM = 6
        private const val DEFAULT_LAT = 23.6
        private const val DEFAULT_LON = -102.5
        private const val DEFAULT_ZOOM = 4.5
        private const val SRC_ROUTE = "ivm-route-src"
        private const val SRC_POINTS = "ivm-points-src"
        private const val LAYER_ROUTE = "ivm-route"
        private const val LAYER_POINTS = "ivm-points"

        private const val EXTRA_NAME = "dest_name"
        private const val EXTRA_LAT = "dest_lat"
        private const val EXTRA_LON = "dest_lon"

        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, MapActivity::class.java))
        }

        /** Open the map already routing to a place (saved place, parking spot…). */
        fun startTo(ctx: Context, name: String, lat: Double, lon: Double) {
            ctx.startActivity(
                Intent(ctx, MapActivity::class.java)
                    .putExtra(EXTRA_NAME, name).putExtra(EXTRA_LAT, lat).putExtra(EXTRA_LON, lon),
            )
        }
    }
}
