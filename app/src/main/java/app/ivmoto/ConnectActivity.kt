// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** IVMoto's connection screen: one button, three clear steps, and a plain-language status. */
class ConnectActivity : AppCompatActivity() {

    private lateinit var flow: ConnectFlow
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var primary: MaterialButton
    private lateinit var stepWifi: TextView
    private lateinit var stepAa: TextView
    private lateinit var stepBike: TextView
    private var pendingQr: QrData? = null
    private var rivalShown = false
    private var pendingDest: MapPlace? = null
    private var aaHelpShown = false

    private val scan = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val raw = r.data?.getStringExtra(ScanActivity.RESULT_QR)
        if (r.resultCode != RESULT_OK || raw == null) return@registerForActivityResult
        val qr = QrData.parse(raw) ?: return@registerForActivityResult
        BikeMemory.save(this, raw, qr)
        proceed(qr)
    }

    private val guide = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) pendingQr?.let { proceed(it) }
    }

    private val askPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        pendingQr?.let { proceed(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_connect)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.connect_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        flow = ConnectFlow(this)
        title = findViewById(R.id.connect_status)
        detail = findViewById(R.id.connect_detail)
        spinner = findViewById(R.id.connect_spinner)
        primary = findViewById(R.id.connect_primary)
        stepWifi = findViewById(R.id.step_wifi)
        stepAa = findViewById(R.id.step_aa)
        stepBike = findViewById(R.id.step_bike)

        findViewById<View>(R.id.connect_back).setOnClickListener { finish() }
        findViewById<View>(R.id.connect_scan).setOnClickListener { scan.launch(Intent(this, ScanActivity::class.java)) }
        findViewById<View>(R.id.connect_stop).setOnClickListener { flow.stop() }
        findViewById<View>(R.id.connect_mode).setOnClickListener {
            if (!isActive(ConnectionState.phase)) {
                ConnectMode.toggle(this)
                render(ConnectionState.phase, ConnectionState.detail)
            }
        }
        findViewById<View>(R.id.connect_guide).setOnClickListener {
            startActivity(Intent(this, PrepareActivity::class.java))
        }
        findViewById<View>(R.id.connect_diag).setOnClickListener { DiagnosticsActivity.start(this) }
        primary.setOnClickListener { onPrimary() }

        ConnectionState.listener = { phase, d -> runOnUiThread { render(phase, d) } }
        render(ConnectionState.phase, ConnectionState.detail)
        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    /** Auto-connect (Home, Bluetooth remote) and the "tap to resume" notification. */
    private fun handleLaunchIntent(i: Intent?) {
        if (i != null && i.hasExtra(EXTRA_DEST_LAT)) {
            pendingDest = MapPlace(i.getStringExtra(EXTRA_DEST_NAME).orEmpty(), i.getDoubleExtra(EXTRA_DEST_LAT, 0.0), i.getDoubleExtra(EXTRA_DEST_LON, 0.0))
            i.removeExtra(EXTRA_DEST_LAT)
            BikeMemory.lastQr(this)?.let { proceed(it) }
            return
        }
        if (i == null || isActive(ConnectionState.phase)) return
        val tapped = i.getBooleanExtra(AndroidAutoService.EXTRA_RESUME, false)
        val auto = i.getBooleanExtra(EXTRA_AUTO, false)
        i.removeExtra(AndroidAutoService.EXTRA_RESUME)
        i.removeExtra(EXTRA_AUTO)
        if (tapped || auto) onPrimary()
    }

    override fun onResume() {
        super.onResume()
        val qr = BikeMemory.lastQr(this) ?: return
        val stalled = isStalled(ConnectionState.phase) || AndroidAutoService.isParked
        if (stalled && !autoResumed && BikeWifi.isSsidInRange(this, qr.ssid) != false) {
            autoResumed = true
            resume()
        }
    }

    private var autoResumed = false

    override fun onDestroy() {
        if (isFinishing) ConnectionState.listener = null
        super.onDestroy()
    }

    private fun isStalled(p: Phase) = p == Phase.WAITING_FOR_BIKE || p == Phase.RECONNECTING

    private fun isActive(p: Phase) = (p.busy && !isStalled(p)) || p == Phase.STREAMING || p == Phase.MIRRORING

    /** Re-start Android Auto from the foreground (Google only allows that from a visible app). */
    private fun resume() {
        val qr = BikeMemory.lastQr(this) ?: return
        AndroidAutoService.notifyForegroundResuming()
        proceed(qr)
    }

    private fun onPrimary() {
        if (isStalled(ConnectionState.phase) || AndroidAutoService.isParked) {
            resume()
            return
        }
        if (isActive(ConnectionState.phase)) {
            flow.stop()
            return
        }
        val qr = BikeMemory.lastQr(this)
        if (qr == null) {
            scan.launch(Intent(this, ScanActivity::class.java))
        } else {
            proceed(qr)
        }
    }

    private fun proceed(qr: QrData) {
        pendingQr = qr
        if (!PrepareActivity.isDone(this)) {
            guide.launch(Intent(this, PrepareActivity::class.java))
            return
        }
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty() && !askedOnce) {
            askedOnce = true
            askPermissions.launch(missing.toTypedArray())
            return
        }
        askedOnce = false
        val dash = ConnectMode.get(this) == ConnectMode.Mode.DASH
        when (flow.blocker(dash)) {
            Blocker.NONE -> if (dash) flow.beginDash(qr, pendingDest.also { pendingDest = null }) else flow.begin(qr)
            Blocker.NEED_ANDROID_AUTO -> askInstallAndroidAuto()
            Blocker.WIFI_OFF -> WifiGate.ensureEnabledOrPrompt(this)
            Blocker.NEED_NEARBY_PERMISSION -> showSettingsDialog(R.string.conn_need_nearby)
            Blocker.NEED_BLUETOOTH_ON -> {
                Toast.makeText(this, R.string.conn_need_bluetooth, Toast.LENGTH_LONG).show()
                runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            }
        }
    }

    private var askedOnce = false

    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun askInstallAndroidAuto() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.conn_need_aa_title)
            .setMessage(R.string.conn_need_aa_message)
            .setPositiveButton(R.string.conn_need_aa_install) { _, _ ->
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ConnectFlow.ANDROID_AUTO_PKG}")))
                }.onFailure {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${ConnectFlow.ANDROID_AUTO_PKG}")))
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSettingsDialog(message: Int) {
        MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setPositiveButton(R.string.conn_open_settings) { _, _ ->
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", packageName, null)),
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** The official CFMoto app can hold the bike's link ports; offer to close it. */
    private fun maybeAskCloseOfficialApp(phase: Phase, d: String) {
        if (phase != Phase.ERROR || !d.contains("CFMoto app", ignoreCase = true)) {
            rivalShown = false
            return
        }
        if (rivalShown) return
        rivalShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.conn_rival_title)
            .setMessage(R.string.conn_rival_message)
            .setPositiveButton(R.string.conn_rival_close) { _, _ ->
                RivalClient.closeBestEffort(this)
                RivalClient.openAppInfo(this)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Android Auto never answered: almost always its head unit server is off (Android Auto 17.4+). */
    private fun maybeExplainAndroidAuto(phase: Phase, d: String) {
        if (phase != Phase.ERROR || d != getString(R.string.conn_detail_aa_not_started)) {
            aaHelpShown = false
            return
        }
        if (aaHelpShown) return
        aaHelpShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.conn_aa_help_title)
            .setMessage(R.string.conn_aa_help_message)
            .setPositiveButton(R.string.conn_aa_help_open) { _, _ ->
                startActivity(Intent(this, PrepareActivity::class.java))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun render(phase: Phase, d: String) {
        val live = phase == Phase.STREAMING || phase == Phase.MIRRORING
        val dash = ConnectMode.get(this) == ConnectMode.Mode.DASH
        findViewById<TextView>(R.id.connect_mode).text =
            getString(R.string.conn_mode_label, getString(if (dash) R.string.conn_mode_dash else R.string.conn_mode_aa))
        stepAa.visibility = if (dash) View.GONE else View.VISIBLE
        title.setText(
            when {
                live -> R.string.conn_live
                phase == Phase.ERROR -> R.string.conn_problem
                phase.busy -> R.string.conn_working
                phase == Phase.STOPPED -> R.string.conn_stopped_title
                else -> R.string.conn_ready_title
            },
        )
        detail.text = when {
            d.isNotBlank() -> d
            phase.busy || live || phase == Phase.ERROR -> getString(phase.labelRes)
            BikeMemory.hasSaved(this) -> getString(R.string.conn_ready_saved, BikeMemory.lastBikeName(this).orEmpty())
            else -> getString(R.string.conn_ready_none)
        }
        spinner.visibility = if (phase.busy && !isStalled(phase)) View.VISIBLE else View.GONE
        findViewById<View>(R.id.connect_stop).visibility = if (isStalled(phase)) View.VISIBLE else View.GONE
        primary.setText(
            when {
                isStalled(phase) -> R.string.conn_btn_reconnect
                isActive(phase) -> R.string.conn_btn_stop
                BikeMemory.hasSaved(this) -> R.string.conn_btn_connect
                else -> R.string.conn_btn_pair
            },
        )
        // Three simple steps: Wi-Fi of the bike, Android Auto, and the dash link.
        val wifiDone = phase in setOf(Phase.AA_VIDEO_LIVE, Phase.PXC_CONNECTING, Phase.STREAMING, Phase.MIRRORING)
        stepWifi.setStep(R.string.conn_step_wifi, wifiDone || phase == Phase.STREAMING, phase == Phase.JOINING_WIFI)
        stepAa.setStep(R.string.conn_step_aa, phase in setOf(Phase.AA_VIDEO_LIVE, Phase.PXC_CONNECTING, Phase.STREAMING), phase == Phase.STARTING_AA)
        stepBike.setStep(R.string.conn_step_bike, live, phase == Phase.PXC_CONNECTING)
        maybeAskCloseOfficialApp(phase, d)
        maybeExplainAndroidAuto(phase, d)
    }

    private fun TextView.setStep(label: Int, done: Boolean, active: Boolean) {
        setText(label)
        val color = when {
            done -> R.color.status_live
            active -> R.color.status_busy
            else -> R.color.text_secondary
        }
        setTextColor(ContextCompat.getColor(context, color))
        alpha = if (done || active) 1f else 0.6f
        setCompoundDrawablesWithIntrinsicBounds(
            if (done) R.drawable.ic_step_done else R.drawable.ic_step_idle, 0, 0, 0,
        )
        compoundDrawableTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, color))
    }

    companion object {
        const val EXTRA_AUTO = "auto_connect"
        private const val EXTRA_DEST_NAME = "dash_dest_name"
        private const val EXTRA_DEST_LAT = "dash_dest_lat"
        private const val EXTRA_DEST_LON = "dash_dest_lon"

        /** Switch to IVMoto's own dash and take [place] to the bike as a route. */
        fun startDash(ctx: Context, place: MapPlace) {
            ConnectMode.set(ctx, ConnectMode.Mode.DASH)
            ctx.startActivity(
                Intent(ctx, ConnectActivity::class.java)
                    .putExtra(EXTRA_DEST_NAME, place.name).putExtra(EXTRA_DEST_LAT, place.lat).putExtra(EXTRA_DEST_LON, place.lon),
            )
        }

        fun start(ctx: Context, auto: Boolean = false) {
            ctx.startActivity(Intent(ctx, ConnectActivity::class.java).putExtra(EXTRA_AUTO, auto))
        }
    }
}
