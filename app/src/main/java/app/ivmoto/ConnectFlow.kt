// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.ivmoto.aa.AaSelfMode

/** What must be fixed before a connection can start. */
enum class Blocker { NONE, NEED_NEARBY_PERMISSION, NEED_BLUETOOTH_ON, NEED_ANDROID_AUTO, WIFI_OFF }

/**
 * IVMoto's connection controller for CFMoto bikes: Android Auto → bike Wi-Fi (SoftAP or Wi-Fi
 * Direct) → dash link. It only orchestrates; the screens live in [ConnectActivity].
 *
 * Process-global pieces ([BikeLink], [ConnectionState]) keep the hand-off alive even if the
 * activity is recreated while Google's Android Auto takes the foreground.
 */
class ConnectFlow(private val activity: AppCompatActivity) {

    private val appCtx = activity.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val prober: EasyConnProber
        get() = BikeLink.prober ?: EasyConnProber(appCtx, LogBus::log).also { BikeLink.prober = it }

    /** Checks everything that would make the start fail silently. */
    fun blocker(dash: Boolean = false): Blocker {
        if (!dash && !isAndroidAutoInstalled()) return Blocker.NEED_ANDROID_AUTO
        if (!WifiGate.isWifiEnabled(appCtx)) return Blocker.WIFI_OFF
        if (!dash && !appCtx.canStartAa()) {
            val needPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(appCtx, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
            return if (needPerm) Blocker.NEED_NEARBY_PERMISSION else Blocker.NEED_BLUETOOTH_ON
        }
        return Blocker.NONE
    }

    fun isAndroidAutoInstalled(): Boolean = try {
        appCtx.packageManager.getPackageInfo(ANDROID_AUTO_PKG, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Starts the whole chain for [qr]. Call only when [blocker] is [Blocker.NONE]. */
    fun begin(qr: QrData) {
        try {
            stopPrevious()
            chooseProfile(qr)
            ConnectionState.set(Phase.STARTING_AA, BikeMemory.lastBikeName(appCtx) ?: qr.ssid)
            LogBus.log("→ connect: starting Android Auto")

            // Android Auto and the Wi-Fi join run in parallel; BikeLink only contacts the bike
            // once both are ready.
            BikeLink.beginHandoff(appCtx)
            AaVideoBridge.onSteadyVideo = {
                AaVideoBridge.onSteadyVideo = null
                ConnectionState.set(Phase.AA_VIDEO_LIVE)
                AndroidAutoService.upgradeForegroundForMicrophone()
                BikeLink.markAaVideoSteady()
            }
            AndroidAutoService.start(activity)
            main.postDelayed({ triggerAndroidAuto() }, 900)
            main.postDelayed({ if (!sessionSettled()) triggerAndroidAuto() }, 4_500)
            main.postDelayed({
                if (!sessionSettled()) {
                    ConnectionState.set(ConnectionState.phase, activity.getString(R.string.conn_hint_server))
                }
            }, 7_000)
            main.postDelayed({ giveUpIfAaNeverStarted() }, 18_000)
            joinBikeWifi(qr, gateOnAa = true)
        } catch (e: Exception) {
            LogBus.log("connect failed: $e")
            CrashGuard.persistSession(appCtx)
            ConnectionState.set(Phase.ERROR, activity.getString(R.string.conn_detail_connect_failed))
        }
    }

    /**
     * IVMoto's own dash (map, speed, turns) with no Android Auto: join the bike Wi-Fi, then open the
     * dash link directly. With a [destination] the dash starts guiding to it.
     */
    fun beginDash(qr: QrData, destination: MapPlace? = null) {
        try {
            stopPrevious()
            chooseProfile(qr)
            val overlays = PlacesStore.list(appCtx).map { MapPlace(it.name, it.lat, it.lon) }
            if (destination != null) GpxSession.prepareNavTo(destination, overlays) else GpxSession.prepareFreeRide(overlays)
            LogBus.log("→ connect: IVMoto dash" + (destination?.let { " to ${it.name}" } ?: ""))
            joinBikeWifi(qr, gateOnAa = false)
        } catch (e: Exception) {
            LogBus.log("dash connect failed: $e")
            CrashGuard.persistSession(appCtx)
            ConnectionState.set(Phase.ERROR, activity.getString(R.string.conn_detail_connect_failed))
        }
    }

    fun stop() {
        LogBus.log("→ stop")
        AaVideoBridge.onSteadyVideo = null
        runCatching { AndroidAutoService.stop(activity) }
        runCatching { prober.stop() }
        ProjectionHolder.projection?.let { runCatching { it.stop() } }
        ProjectionHolder.projection = null
        runCatching { GpxSession.clear() }
        runCatching { ProjectionService.stop(activity) }
        runCatching { DashClockBle.stop() }
        runCatching { BikeWifi.releaseSession(appCtx, LogBus::log) }
        ConnectionState.set(Phase.STOPPED, "")
    }

    // ---- internals -------------------------------------------------------------------------

    private fun triggerAndroidAuto() {
        runCatching { AaSelfMode.trigger(activity, log = LogBus::log) }
            .onFailure { LogBus.log("android auto trigger failed: $it") }
    }

    private fun sessionSettled(): Boolean {
        val p = ConnectionState.phase
        return AaVideoBridge.aaSessionLive || AaVideoBridge.aaDecoding ||
            p == Phase.AA_VIDEO_LIVE || p == Phase.PXC_CONNECTING || p == Phase.STREAMING ||
            p == Phase.MIRRORING || p == Phase.IDLE || p == Phase.STOPPED || p == Phase.ERROR
    }

    private fun giveUpIfAaNeverStarted() {
        if (sessionSettled()) return
        LogBus.log("android auto never attached — stopping")
        val res = if (AaVideoBridge.aaSessionSeen) R.string.conn_detail_aa_dropped
        else R.string.conn_detail_aa_not_started
        ConnectionState.set(Phase.ERROR, activity.getString(res))
    }

    /** Releases anything a previous projection left behind so the next session opens cleanly. */
    private fun stopPrevious() {
        runCatching { AaVideoBridge.onSteadyVideo = null }
        AaVideoBridge.pipeline = null
        AaVideoBridge.touchSink = null
        AaVideoBridge.keySink = null
        AaVideoBridge.scrollSink = null
        AaVideoBridge.previewTouchSink = null
        AaVideoBridge.nightSink = null
        runCatching { AndroidAutoService.stop(activity) }
        runCatching { BikeLink.prober?.stop() }
        ProjectionHolder.projection?.let { runCatching { it.stop() } }
        ProjectionHolder.projection = null
        runCatching { ProjectionService.stop(activity) }
        MirrorOrientationLock.clear(activity)
        runCatching { GpxSession.clear() }
        MapInputBridge.clear()
        BikeLink.beginHandoff(appCtx)
    }

    /** The QR picks the screen profile (resolution/orientation) that Android Auto must use. */
    private fun chooseProfile(qr: QrData) {
        AppSettings.applyToHolder(appCtx)
        val profile = BikeProfiles.selectByQr(qr, appCtx)
        BikeProfileHolder.active = profile
        DashMemory.setLastDashTouch(appCtx, BikeProfileHolder.advertisesScreenTouch)
        val override = VideoPrefs.resolutionOverride(appCtx, profile)
        val learned = if (override == null) DashMemory.specFor(appCtx, qr.ssid, profile) else null
        BikeProfileHolder.aaVideoOverride = override ?: learned
        BikeProfileHolder.aaContentMargins =
            VideoPrefs.aaMarginsFor(appCtx, BikeProfileHolder.aaVideo, qr.ssid)
        PodChoice.autoSetup(appCtx, profile)
        LogBus.log("→ bike profile: ${profile.name}")
    }

    private fun joinBikeWifi(qr: QrData, gateOnAa: Boolean) {
        ConnectionState.set(Phase.JOINING_WIFI)
        if (qr.supportsPhoneHotspot && qr.pwd.isEmpty()) {
            ConnectionState.set(Phase.ERROR, activity.getString(R.string.conn_detail_unsupported_bike))
            return
        }
        val saved = BikeMemory.winningTransport(appCtx, qr.ssid)
        val setting = AppSettings.transport(appCtx)
        val wantP2p = when (setting) {
            WifiTransport.P2P -> qr.supportsP2p || !qr.supportsAp
            WifiTransport.AP -> false
            WifiTransport.AUTO -> when {
                saved == "AP" && qr.supportsAp -> false
                saved == "P2P" && qr.supportsP2p -> true
                else -> (qr.supportsP2p && !qr.supportsAp) ||
                    (qr.ssid.startsWith("DIRECT-", ignoreCase = true) && (qr.supportsP2p || !qr.supportsAp))
            }
        }
        // A P2P-only QR with a normal SSID, no MAC and a password is really a SoftAP.
        val direct = qr.ssid.startsWith("DIRECT-", ignoreCase = true)
        if (wantP2p && !(!direct && qr.mac.isNullOrBlank() && qr.pwd.isNotEmpty())) {
            joinP2p(qr, gateOnAa)
        } else {
            joinAp(qr, gateOnAa)
        }
    }

    private fun joinAp(qr: QrData, gateOnAa: Boolean) {
        BikeWifi.reuseOrJoin(
            context = appCtx,
            ssid = qr.ssid,
            psk = qr.pwd,
            onAvailable = { network ->
                WifiGate.cancelNotification(appCtx)
                BikeMemory.setWinningTransport(appCtx, qr.ssid, "AP")
                LogBus.log("→ bike Wi-Fi ready")
                if (gateOnAa) {
                    BikeLink.markWifiReady(network)
                } else {
                    ConnectionState.set(Phase.PXC_CONNECTING)
                    runCatching { prober.start(network ?: BikeWifi.currentNetwork) }
                        .onFailure { LogBus.log("dash link start failed: $it") }
                }
            },
            onLost = { LogBus.log("bike network lost") },
            log = LogBus::log,
        )
    }

    private fun joinP2p(qr: QrData, gateOnAa: Boolean) {
        val known = BikeMemory.winningTransport(appCtx, qr.ssid)
        val timeout = when {
            known == "P2P" -> BikeWifiP2p.CONNECT_TIMEOUT_MS
            qr.supportsAp && qr.pwd.isNotEmpty() -> 12_000L
            else -> BikeWifiP2p.CONNECT_TIMEOUT_MS
        }
        BikeWifiP2p.connect(
            context = appCtx,
            qr = qr,
            timeoutMs = timeout,
            onConnected = { bindIp, gatewayIp ->
                WifiGate.cancelNotification(appCtx)
                BikeMemory.setWinningTransport(appCtx, qr.ssid, "P2P")
                LogBus.log("→ Wi-Fi Direct ready")
                if (gateOnAa) {
                    BikeLink.markP2pReady(bindIp, gatewayIp)
                } else {
                    ConnectionState.set(Phase.PXC_CONNECTING)
                    runCatching { prober.start(network = null, gatewayOverride = gatewayIp, bindIpOverride = bindIp) }
                        .onFailure { LogBus.log("dash link start failed: $it") }
                }
            },
            onFailed = { reason ->
                LogBus.log("Wi-Fi Direct failed: $reason")
                if (qr.pwd.isEmpty()) {
                    ConnectionState.set(Phase.ERROR, activity.getString(R.string.conn_detail_connect_failed))
                } else {
                    joinAp(qr, gateOnAa)
                }
            },
            log = LogBus::log,
        )
    }

    companion object {
        const val ANDROID_AUTO_PKG = "com.google.android.projection.gearhead"
    }
}

private fun android.content.Context.canStartAa(): Boolean =
    AaSelfMode.canAttemptHur(this)
