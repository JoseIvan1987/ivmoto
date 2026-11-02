// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context

/** A rider's nickname for each paired bike ("La Bestia"), kept on the phone. */
object Nicknames {
    private const val PREFS = "ivmoto_nicknames"
    private const val MAX = 20

    private fun key(raw: String?) = if (raw == null) "default" else BikeScope.idFor(raw)

    fun get(ctx: Context, raw: String?): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(raw), null)?.takeIf { it.isNotBlank() }

    fun set(ctx: Context, raw: String?, nickname: String?) {
        val value = nickname?.trim().orEmpty().take(MAX)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (value.isEmpty()) remove(key(raw)) else putString(key(raw), value)
        }.apply()
    }
}
