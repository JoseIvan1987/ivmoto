// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import org.json.JSONObject
import java.io.InputStream

/** What the update manifest (latest.json) says about the newest release. */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
)

object UpdateRules {
    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Parses and validates the manifest; anything incomplete or unsafe (http, bad hash) gives null. */
    fun parse(json: String): UpdateManifest? {
        val o = runCatching { JSONObject(json.removePrefix("\uFEFF").trim()) }.getOrNull() ?: return null
        val code = o.optInt("versionCode", 0)
        val name = o.optString("versionName", "").trim()
        val url = o.optString("apkUrl", "").trim()
        val sha = o.optString("sha256", "").trim()
        if (code <= 0 || name.isEmpty() || !url.startsWith("https://") || !HEX64.matches(sha)) return null
        return UpdateManifest(code, name, url, sha.lowercase(), o.optString("notes", "").take(800))
    }

    /** Reads at most [max] bytes as UTF-8 text. (InputStream.readNBytes does not exist before Android 13.) */
    fun readLimited(input: InputStream, max: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (out.size() < max) {
            val n = input.read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    fun isNewer(remoteCode: Int, currentCode: Int): Boolean = remoteCode > currentCode

    fun hashMatches(expected: String, actual: String): Boolean = expected.trim().equals(actual.trim(), ignoreCase = true)

    fun checkDue(nowMs: Long, lastMs: Long): Boolean = nowMs - lastMs >= DAY_MS
}
