// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context

/** Which handlebar pod the rider has (BACK/SET, MODE/ENT or 5-way) and the sensible default per bike. */
object PodChoice {
    private const val PREFS = "ivmoto_buttons_ui"
    private const val KEY = "pod"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The pod the rider (or the auto setup) chose, or null when nothing was chosen yet. */
    fun current(ctx: Context): ButtonClusterPreset? {
        val id = prefs(ctx).getString(KEY, null) ?: return null
        return ButtonClusterPreset.entries.firstOrNull { it.id == id }
    }

    fun set(ctx: Context, preset: ButtonClusterPreset) {
        preset.apply(ctx)
        prefs(ctx).edit().putString(KEY, preset.id).apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY).apply()
    }

    /** The 675 (CFDL16 dash) ships with the BACK / SET pod; other bikes keep the generic default. */
    fun recommended(profile: BikeProfile?): ButtonClusterPreset? =
        if (profile === LegacyCfdl16Profile) ButtonClusterPreset.BACK_SET else null

    /** On first connect, pick the recommended pod, but never overwrite buttons the rider customized. */
    fun autoSetup(ctx: Context, profile: BikeProfile?) {
        if (current(ctx) != null || !ButtonMap.isAllDefault(ctx)) return
        recommended(profile)?.let { set(ctx, it) }
    }
}
