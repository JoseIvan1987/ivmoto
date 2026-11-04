// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.app.PendingIntent
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * In-app updates. The app reads a small manifest from the latest GitHub release, downloads the APK, checks its
 * SHA-256 and that it is signed by the same key as the installed app, and hands it to Android's
 * installer (the rider taps once to confirm). Nothing is installed that fails those checks.
 */
object AppUpdater {
    /** The newest release's latest.json (GitHub keeps this address pointing at the latest release). */
    const val MANIFEST_URL = "https://github.com/JoseIvan1987/ivmoto/releases/latest/download/latest.json"

    private const val PREFS = "ivmoto_updates"
    private const val MAX_APK_BYTES = 200L * 1024 * 1024

    sealed class Check {
        object UpToDate : Check()
        object Failed : Check()
        data class Available(val manifest: UpdateManifest) : Check()
    }

    enum class Problem { DOWNLOAD, HASH, SIGNATURE, MISMATCH }
    class Download(val file: File?, val problem: Problem?)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)


    fun autoEnabled(ctx: Context) = prefs(ctx).getBoolean("auto", true)
    fun setAuto(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("auto", on).apply()

    fun shouldCheck(ctx: Context) =
        autoEnabled(ctx) && UpdateRules.checkDue(System.currentTimeMillis(), prefs(ctx).getLong("last", 0L))

    /** The newest release found by the last check, if it is newer than this install. */
    fun pending(ctx: Context): UpdateManifest? {
        val m = prefs(ctx).getString("pending", null)?.let { UpdateRules.parse(it) } ?: return null
        return if (UpdateRules.isNewer(m.versionCode, BuildConfig.VERSION_CODE)) m else null
    }

    /** Blocking: call off the main thread. */
    fun checkNow(ctx: Context): Check {
        val p = prefs(ctx)
        val text = fetchText(MANIFEST_URL) ?: return Check.Failed
        p.edit().putLong("last", System.currentTimeMillis()).apply()
        val m = UpdateRules.parse(text)
        if (m == null) LogBus.log("[UPDATE] the manifest is not valid")
        if (m == null || !UpdateRules.isNewer(m.versionCode, BuildConfig.VERSION_CODE)) {
            p.edit().remove("pending").apply()
            return if (m == null) Check.Failed else Check.UpToDate
        }
        p.edit().putString("pending", text).apply()
        return Check.Available(m)
    }

    private fun fetchText(url: String): String? = try {
        val conn = AppHttp.openUrl(url).apply { connectTimeout = 10_000; readTimeout = 10_000 }
        try {
            if (conn.responseCode != 200) {
                LogBus.log("[UPDATE] manifest answered HTTP ${conn.responseCode}")
                null
            } else {
                conn.inputStream.use { UpdateRules.readLimited(it, 64 * 1024) }
            }
        } finally {
            conn.disconnect()
        }
    } catch (t: Throwable) {
        LogBus.log("[UPDATE] could not read the manifest: $t")
        null
    }

    /** Blocking: downloads and verifies the APK. [onProgress] gets 0..100. */
    fun download(ctx: Context, m: UpdateManifest, onProgress: (Int) -> Unit): Download {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "IVMoto-${m.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val conn = AppHttp.openUrl(m.apkUrl).apply { connectTimeout = 15_000; readTimeout = 30_000 }
            try {
                if (conn.responseCode != 200) return Download(null, Problem.DOWNLOAD)
                val total = conn.contentLengthLong
                if (total > MAX_APK_BYTES) return Download(null, Problem.DOWNLOAD)
                var written = 0L
                var lastPercent = -1
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            written += n
                            if (written > MAX_APK_BYTES) { out.delete(); return Download(null, Problem.DOWNLOAD) }
                            digest.update(buf, 0, n)
                            output.write(buf, 0, n)
                            if (total > 0) {
                                val percent = (written * 100 / total).toInt()
                                if (percent != lastPercent) { lastPercent = percent; onProgress(percent) }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            out.delete()
            return Download(null, Problem.DOWNLOAD)
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (!UpdateRules.hashMatches(m.sha256, hex)) { out.delete(); return Download(null, Problem.HASH) }
        val info = ctx.packageManager.getPackageArchiveInfo(out.path, PackageManager.GET_SIGNING_CERTIFICATES)
        if (info == null || info.packageName != ctx.packageName || longCode(info) != m.versionCode.toLong()) {
            out.delete(); return Download(null, Problem.MISMATCH)
        }
        if (!sameSigner(ctx, info)) { out.delete(); return Download(null, Problem.SIGNATURE) }
        return Download(out, null)
    }

    @Suppress("DEPRECATION")
    private fun longCode(info: PackageInfo): Long = info.longVersionCode

    private fun signers(info: PackageInfo?): Set<String> {
        val si = info?.signingInfo ?: return emptySet()
        val certs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        return certs.map { c -> MessageDigest.getInstance("SHA-256").digest(c.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    /** True when the downloaded APK is signed by the same key as the app that is installed now. */
    private fun sameSigner(ctx: Context, apkInfo: PackageInfo): Boolean {
        val mine = signers(ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
        val theirs = signers(apkInfo)
        return mine.isNotEmpty() && theirs.isNotEmpty() && mine.intersect(theirs).isNotEmpty()
    }

    /** Hands a verified APK to Android's installer; the rider confirms on the system screen. */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("ivmoto.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, UpdateInstallReceiver::class.java).setPackage(ctx.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
        }
    }
}
