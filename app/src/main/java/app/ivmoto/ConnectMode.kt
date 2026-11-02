// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context

/** What the bike's screen shows: Android Auto (Google's apps) or IVMoto's own dash. */
object ConnectMode {
    enum class Mode { ANDROID_AUTO, DASH }

    private const val PREFS = "ivmoto_connect_mode"

    fun get(ctx: Context): Mode =
        if (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", null) == "dash") Mode.DASH else Mode.ANDROID_AUTO

    fun set(ctx: Context, mode: Mode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", if (mode == Mode.DASH) "dash" else "aa").apply()
    }

    fun toggle(ctx: Context) = set(ctx, if (get(ctx) == Mode.DASH) Mode.ANDROID_AUTO else Mode.DASH)
}
