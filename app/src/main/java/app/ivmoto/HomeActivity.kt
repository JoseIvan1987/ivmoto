// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

/** "Mi moto": the simple front door. One big button; everything technical lives behind Ajustes. */
class HomeActivity : AppCompatActivity() {

    private lateinit var chip: TextView
    private lateinit var model: TextView
    private lateinit var kind: TextView
    private lateinit var parked: TextView
    private lateinit var photo: ImageView
    private lateinit var connect: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        chip = findViewById(R.id.home_chip)
        model = findViewById(R.id.home_model)
        kind = findViewById(R.id.home_kind)
        parked = findViewById(R.id.home_parked)
        findViewById<TextView>(R.id.home_version).text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
        findViewById<TextView>(R.id.home_nickname).setOnClickListener { askNickname() }
        photo = findViewById(R.id.home_photo)
        connect = findViewById(R.id.home_connect)

        connect.setOnClickListener { onConnectPressed() }
        findViewById<TextView>(R.id.home_tile_map).setOnClickListener { MapActivity.start(this) }
        findViewById<TextView>(R.id.home_tile_trips).setOnClickListener { TripsActivity.start(this) }
        findViewById<TextView>(R.id.home_tile_places).setOnClickListener { PlacesActivity.start(this) }
        findViewById<TextView>(R.id.home_tile_maint).setOnClickListener { MaintenanceActivity.start(this) }
        findViewById<TextView>(R.id.home_tile_fuel).setOnClickListener { FuelActivity.start(this) }
        findViewById<TextView>(R.id.home_tile_sos).setOnClickListener { EmergencyActivity.start(this) }
        findViewById<TextView>(R.id.home_alert).setOnClickListener { MaintenanceActivity.start(this) }
        val openSettings = { SettingsActivity.start(this) }
        findViewById<android.view.View>(R.id.home_settings).setOnClickListener { openSettings() }
        findViewById<TextView>(R.id.home_change_bike).setOnClickListener { BikePickerActivity.start(this) }
        findViewById<TextView>(R.id.home_donate).setOnClickListener { DonateActivity.start(this) }
        AppLinks.linkCredit(findViewById(R.id.home_created_by))
    }

    override fun onResume() {
        super.onResume()
        render()
        renderUpdate()
        if (AppUpdater.shouldCheck(this)) {
            kotlin.concurrent.thread {
                AppUpdater.checkNow(this)
                runOnUiThread { renderUpdate() }
            }
        }
    }

    private fun renderUpdate() {
        val banner = findViewById<TextView>(R.id.home_update)
        val m = AppUpdater.pending(this)
        if (m == null) {
            banner.visibility = android.view.View.GONE
        } else {
            banner.visibility = android.view.View.VISIBLE
            banner.setOnClickListener { UpdateFlow.offer(this, m) }
        }
    }

    private fun render() {
        val saved = BikeMemory.selected(this)
        val catalog = BikeCatalog.byId(AppSettings.selectedModelId(this))
        val nick = Nicknames.get(this, saved?.raw)
        val alertView = findViewById<TextView>(R.id.home_alert)
        val urgent = MaintenanceStore.mostUrgent(this)
        if (urgent != null) {
            val (task, left) = urgent
            val name = task.name ?: getString(
                when (task.id) {
                    "oil" -> R.string.maint_oil
                    "chain" -> R.string.maint_chain
                    "tires" -> R.string.maint_tires
                    "brakes" -> R.string.maint_brakes
                    else -> R.string.maint_air
                },
            )
            alertView.text = if (left <= 0) getString(R.string.home_alert_due, name) else getString(R.string.home_alert_soon, name, left)
            alertView.visibility = android.view.View.VISIBLE
        } else {
            alertView.visibility = android.view.View.GONE
        }
        model.text = catalog?.name ?: saved?.name ?: getString(R.string.home_no_bike)
        kind.text = if (catalog != null) "CFMoto · ${catalog.kind}" else ""
        findViewById<TextView>(R.id.home_nickname).apply {
            if (nick != null) {
                text = getString(R.string.home_nickname_show, nick)
                textSize = 22f
                setTextColor(getColor(R.color.text_primary))
            } else {
                setText(R.string.home_nickname_set)
                textSize = 14f
                setTextColor(getColor(R.color.ivm_cyan))
            }
        }
        val spot = ParkingSpot.get(this)
        val liveNow = ConnectionState.phase == Phase.STREAMING || ConnectionState.phase == Phase.MIRRORING
        if (spot != null && !liveNow) {
            parked.text = getString(R.string.parked_label, android.text.format.DateUtils.getRelativeTimeSpanString(spot.time))
            parked.visibility = android.view.View.VISIBLE
            parked.setOnClickListener { MapActivity.startTo(this, getString(R.string.parked_name), spot.lat, spot.lon) }
        } else {
            parked.visibility = android.view.View.GONE
        }

        val bmp = BikePhotos.load(saved?.photoPath, 1000) ?: ModelPhotos.load(this, catalog?.id, 1000)
        if (bmp != null) {
            photo.scaleType = ImageView.ScaleType.CENTER_CROP
            photo.setImageBitmap(bmp)
        } else {
            photo.scaleType = ImageView.ScaleType.FIT_CENTER
            photo.setImageResource(R.drawable.ic_moto_illustration)
        }

        val phase = ConnectionState.phase
        val live = phase == Phase.STREAMING || phase == Phase.MIRRORING
        val (text, color) = when {
            live -> getString(phase.labelRes) to R.color.status_live
            phase.busy -> getString(phase.labelRes) to R.color.status_busy
            phase == Phase.ERROR -> getString(phase.labelRes) to R.color.status_error
            saved != null -> getString(R.string.home_status_ready) to R.color.status_live
            else -> getString(R.string.home_status_none) to R.color.status_idle
        }
        chip.text = text
        chip.setTextColor(ContextCompat.getColor(this, color))
        connect.setText(
            when {
                live || phase.busy -> R.string.home_view
                saved != null -> R.string.home_connect
                else -> R.string.home_pair
            },
        )
    }

    private fun askNickname() {
        val raw = BikeMemory.selected(this)?.raw
        val input = android.widget.EditText(this).apply {
            setText(Nicknames.get(this@HomeActivity, raw).orEmpty())
            setSelection(text.length)
            hint = getString(R.string.nick_hint)
            filters = arrayOf(android.text.InputFilter.LengthFilter(20))
            setPadding(48, 32, 48, 16)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nick_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Nicknames.set(this, raw, input.text.toString())
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onConnectPressed() {
        ConnectActivity.start(this, auto = BikeMemory.hasSaved(this))
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, HomeActivity::class.java))
        }
    }
}
