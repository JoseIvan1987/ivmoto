// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch

/** IVMoto settings: a short, grouped list of choices a rider understands without a manual. */
class SettingsActivity : AppCompatActivity() {

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
        findViewById<View>(R.id.settings_back).setOnClickListener { finish() }
        list = findViewById(R.id.settings_list)
        build()
    }

    private fun build() {
        list.removeAllViews()

        section(R.string.st_sec_bike)
        link(R.string.st_my_bike, R.string.st_my_bike_desc) { BikePickerActivity.start(this) }
        link(R.string.st_garage, R.string.st_garage_desc) { BikesActivity.start(this) }

        section(R.string.st_sec_screen)
        choice(
            R.string.st_quality, R.string.st_quality_desc,
            VideoQuality.entries.map { Opt(getString(it.labelRes), VideoPrefs.get(this) == it) { VideoPrefs.set(this, it) } },
        )
        choice(
            R.string.st_power, R.string.st_power_desc,
            PowerMode.entries.map { Opt(getString(it.labelRes), VideoPrefs.power(this) == it) { VideoPrefs.setPower(this, it) } },
        )
        choice(
            R.string.st_fit, R.string.st_fit_desc,
            ScreenFit.entries.map { Opt(getString(it.labelRes), VideoPrefs.fit(this) == it) { VideoPrefs.setFit(this, it) } },
        )
        choice(
            R.string.st_touch, R.string.st_touch_desc,
            listOf(
                Opt(getString(R.string.st_touch_auto), !AppSettings.forceTouch(this) && !AppSettings.forceNonTouch(this)) {
                    AppSettings.setForceTouch(this, false)
                    AppSettings.setForceNonTouch(this, false)
                },
                Opt(getString(R.string.st_touch_on), AppSettings.forceTouch(this)) { AppSettings.setForceTouch(this, true) },
                Opt(getString(R.string.st_touch_off), AppSettings.forceNonTouch(this)) { AppSettings.setForceNonTouch(this, true) },
            ),
        )
        link(R.string.st_buttons, R.string.st_buttons_desc) { ButtonsActivity.start(this) }

        link(R.string.off_title, R.string.off_settings_desc) { OfflineMapsActivity.start(this) }

        section(R.string.st_sec_connection)
        choice(
            R.string.st_mode, R.string.st_mode_desc,
            listOf(
                Opt(getString(R.string.conn_mode_aa), ConnectMode.get(this) == ConnectMode.Mode.ANDROID_AUTO) {
                    ConnectMode.set(this, ConnectMode.Mode.ANDROID_AUTO)
                },
                Opt(getString(R.string.conn_mode_dash), ConnectMode.get(this) == ConnectMode.Mode.DASH) {
                    ConnectMode.set(this, ConnectMode.Mode.DASH)
                },
            ),
        )
        toggle(R.string.st_auto_connect, R.string.st_auto_connect_desc,
            { AppSettings.autoConnect(this) }, { AppSettings.setAutoConnect(this, it) })
        toggle(R.string.st_auto_recovery, R.string.st_auto_recovery_desc,
            { AppSettings.autoRecovery(this) }, { AppSettings.setAutoRecovery(this, it) })
        toggle(R.string.st_keep_wifi, R.string.st_keep_wifi_desc,
            { AppSettings.keepWifiAfterDisconnect(this) }, { AppSettings.setKeepWifiAfterDisconnect(this, it) })
        choice(
            R.string.st_wifi, R.string.st_wifi_desc,
            listOf(
                Opt(getString(R.string.st_wifi_auto), AppSettings.transport(this) == WifiTransport.AUTO) {
                    AppSettings.setTransport(this, WifiTransport.AUTO)
                },
                Opt(getString(R.string.st_wifi_ap), AppSettings.transport(this) == WifiTransport.AP) {
                    AppSettings.setTransport(this, WifiTransport.AP)
                },
                Opt(getString(R.string.st_wifi_direct), AppSettings.transport(this) == WifiTransport.P2P) {
                    AppSettings.setTransport(this, WifiTransport.P2P)
                },
            ),
        )

        section(R.string.st_sec_rides)
        toggle(R.string.st_log_trips, R.string.st_log_trips_desc,
            { AppSettings.logTrips(this) }, { AppSettings.setLogTrips(this, it) })

        section(R.string.st_sec_safety)
        toggle(R.string.st_speed_alert, R.string.st_speed_alert_desc,
            { SpeedAlert.enabled(this) }, { SpeedAlert.setEnabled(this, it) })
        choice(
            R.string.st_speed_limit, R.string.st_speed_limit_desc,
            listOf(60, 80, 100, 120).map { v -> Opt("$v km/h", SpeedAlert.limit(this) == v) { SpeedAlert.setLimit(this, v) } },
        )

        section(R.string.st_sec_look)
        choice(
            R.string.st_theme, R.string.st_theme_desc,
            listOf("light" to R.string.st_theme_light, "dark" to R.string.st_theme_dark, "auto" to R.string.st_theme_auto).map { (v, label) ->
                Opt(getString(label), AppSettings.theme(this) == v) { AppSettings.setTheme(this, v); recreate() }
            },
        )

        section(R.string.st_sec_ride_safety)
        toggle(R.string.st_rest, R.string.st_rest_desc,
            { RideSettings.restEnabled(this) }, { RideSettings.setRestEnabled(this, it) })
        choice(
            R.string.st_rest_every, R.string.st_rest_every_desc,
            listOf(1, 2, 3).map { h -> Opt("$h h", RideSettings.restHours(this) == h) { RideSettings.setRestHours(this, h) } },
        )
        toggle(R.string.st_road_limit, R.string.st_road_limit_desc,
            { RideSettings.roadLimit(this) }, { RideSettings.setRoadLimit(this, it) })
        toggle(R.string.st_curves, R.string.st_curves_desc,
            { RideSettings.curves(this) }, { RideSettings.setCurves(this, it) })
        toggle(R.string.st_avoid_dirt, R.string.st_avoid_dirt_desc,
            { RideSettings.avoidDirt(this) }, { RideSettings.setAvoidDirt(this, it) })

        section(R.string.st_sec_updates)
        toggle(R.string.st_upd_auto, R.string.st_upd_auto_desc,
            { AppUpdater.autoEnabled(this) }, { AppUpdater.setAuto(this, it) })
        link(R.string.st_upd_now, R.string.st_upd_now_desc) { UpdateFlow.checkManually(this) }

        section(R.string.st_sec_help)
        link(R.string.st_about, R.string.st_about_desc) { LicensesActivity.start(this) }
    }

    // ---- row builders ---------------------------------------------------------------------

    private class Opt(val label: String, val selected: Boolean, val onPick: () -> Unit)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun section(title: Int) {
        list.addView(TextView(this).apply {
            setText(title)
            setTextColor(getColor(R.color.ivm_cyan))
            textSize = 12f
            isAllCaps = true
            letterSpacing = 0.08f
            setPadding(dp(4), dp(22), 0, dp(2))
        })
    }

    private fun card(title: Int, desc: Int): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_tile)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) }
        }
        card.addView(TextView(this).apply {
            setText(title)
            setTextColor(getColor(R.color.text_primary))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            setText(desc)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
        })
        list.addView(card)
        return card
    }

    private fun link(title: Int, desc: Int, onClick: () -> Unit) {
        card(title, desc).setOnClickListener { onClick() }
    }

    private fun toggle(title: Int, desc: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
        val card = card(title, desc)
        val sw = MaterialSwitch(this).apply {
            isChecked = get()
            setOnCheckedChangeListener { _, on -> set(on) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.END; topMargin = dp(6) }
        }
        card.addView(sw)
        card.setOnClickListener { sw.toggle() }
    }

    private fun choice(title: Int, desc: Int, options: List<Opt>) {
        val card = card(title, desc)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) }
        }
        val chips = options.map { opt ->
            TextView(this).apply {
                text = opt.label
                gravity = Gravity.CENTER
                textSize = 12f
                maxLines = 2
                setPadding(dp(6), dp(11), dp(6), dp(11))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(3); marginEnd = dp(3) }
            }
        }
        fun paint(selectedIndex: Int) = chips.forEachIndexed { i, chip ->
            val on = i == selectedIndex
            chip.setBackgroundResource(if (on) R.drawable.bg_card_selected else R.drawable.bg_chip)
            chip.setTextColor(getColor(if (on) R.color.text_primary else R.color.text_secondary))
        }
        paint(options.indexOfFirst { it.selected })
        chips.forEachIndexed { i, chip ->
            chip.setOnClickListener {
                options[i].onPick()
                paint(i)
            }
            row.addView(chip)
        }
        card.addView(row)
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
        }
    }
}
