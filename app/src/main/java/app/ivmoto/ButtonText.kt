// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context

/** Plain-language names for handlebar actions and gestures, shared by the button screens. */
object ButtonText {
    fun action(ctx: Context, a: ButtonAction): String = when (a) {
        ButtonAction.NAV_1, ButtonAction.NAV_2, ButtonAction.NAV_3 -> {
            val slot = when (a) { ButtonAction.NAV_1 -> 0; ButtonAction.NAV_2 -> 1; else -> 2 }
            if (SavedPlaces.isSet(ctx, slot)) {
                ctx.getString(R.string.ba_nav_to, SavedPlaces.name(ctx, slot).ifBlank { SavedPlaces.query(ctx, slot) })
            } else {
                ctx.getString(
                    when (a) {
                        ButtonAction.NAV_1 -> R.string.ba_nav1
                        ButtonAction.NAV_2 -> R.string.ba_nav2
                        else -> R.string.ba_nav3
                    },
                )
            }
        }
        else -> ctx.getString(
            when (a) {
                ButtonAction.NONE -> R.string.ba_none
                ButtonAction.KNOB_FORWARD -> R.string.ba_knob_fwd
                ButtonAction.KNOB_BACK -> R.string.ba_knob_back
                ButtonAction.SELECT -> R.string.ba_select
                ButtonAction.BACK -> R.string.ba_back
                ButtonAction.HOME -> R.string.ba_home
                ButtonAction.ASSISTANT -> R.string.ba_assistant
                ButtonAction.DPAD_UP -> R.string.ba_up
                ButtonAction.DPAD_DOWN -> R.string.ba_down
                ButtonAction.DPAD_LEFT -> R.string.ba_left
                ButtonAction.DPAD_RIGHT -> R.string.ba_right
                else -> R.string.ba_none
            },
        )
    }
}
