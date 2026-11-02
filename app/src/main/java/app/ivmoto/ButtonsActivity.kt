// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/** Handlebar buttons: say which kind of control pod you have, then choose what each press does. */
class ButtonsActivity : AppCompatActivity() {

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
        findViewById<TextView>(R.id.settings_title).setText(R.string.st_buttons)
        findViewById<View>(R.id.settings_back).setOnClickListener { finish() }
        list = findViewById(R.id.settings_list)
        ButtonMap.ensureDefaultsMigrated(this)
        build()
    }

    private fun build() {
        list.removeAllViews()

        card(getString(R.string.bt_open_pad), getString(R.string.bt_open_pad_desc))
            .setOnClickListener { ControllerActivity.start(this) }

        // Master switch
        val master = card(getString(R.string.bt_master), getString(R.string.bt_master_desc))
        val sw = MaterialSwitch(this).apply {
            isChecked = ButtonMode.isControlAa(this@ButtonsActivity)
            setOnCheckedChangeListener { _, on -> ButtonMode.set(this@ButtonsActivity, on) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.END; topMargin = dp(6) }
        }
        master.addView(sw)
        master.setOnClickListener { sw.toggle() }

        // Which control pod
        section(R.string.bt_sec_pod)
        val current = PodChoice.current(this)?.id
        val recommended = PodChoice.recommended(BikeProfileHolder.active)
        ButtonClusterPreset.entries.forEach { preset ->
            val tag = if (preset == recommended) "  ·  " + getString(R.string.bt_recommended) else ""
            val c = card(podName(preset) + tag, podDesc(preset))
            c.setBackgroundResource(if (preset.id == current) R.drawable.bg_card_selected else R.drawable.bg_tile)
            c.setOnClickListener {
                PodChoice.set(this, preset)
                build()
            }
        }

        // Per-gesture actions
        section(R.string.bt_sec_actions)
        ButtonGesture.entries.forEach { g ->
            val action = ButtonMap.get(this, g)
            val c = card(gestureName(g), gestureHint(g))
            c.addView(TextView(this).apply {
                text = actionName(action) + "  ›"
                setTextColor(getColor(R.color.ivm_cyan))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
            })
            c.setOnClickListener { pickAction(g) }
        }

        // Places for the "go to saved place" actions
        section(R.string.bt_sec_places)
        for (slot in 0 until SavedPlaces.COUNT) {
            val set = SavedPlaces.isSet(this, slot)
            val c = card(
                getString(R.string.bt_place_slot, slot + 1),
                if (set) SavedPlaces.name(this, slot).ifBlank { SavedPlaces.query(this, slot) } else getString(R.string.bt_place_none),
            )
            c.setOnClickListener { pickPlace(slot) }
        }

        // Reset
        card(getString(R.string.bt_reset), getString(R.string.bt_reset_desc)).setOnClickListener {
            ButtonMap.resetAll(this)
            PodChoice.clear(this)
            PodChoice.autoSetup(this, BikeProfileHolder.active)
            build()
        }
    }

    private fun pickPlace(slot: Int) {
        val places = PlacesStore.list(this)
        if (places.isEmpty()) {
            android.widget.Toast.makeText(this, R.string.bt_place_save_first, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val names = places.map { it.name } + getString(R.string.maint_remove)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.bt_place_slot, slot + 1))
            .setItems(names.toTypedArray()) { _, i ->
                if (i < places.size) {
                    val p = places[i]
                    SavedPlaces.set(this, slot, p.name, String.format(java.util.Locale.US, "%.6f,%.6f", p.lat, p.lon))
                } else {
                    SavedPlaces.set(this, slot, "", "")
                }
                build()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickAction(g: ButtonGesture) {
        val actions = ButtonAction.entries
        MaterialAlertDialogBuilder(this)
            .setTitle(gestureName(g))
            .setItems(actions.map { actionName(it) }.toTypedArray()) { _, i ->
                ButtonMap.set(this, g, actions[i])
                build()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- text ----------------------------------------------------------------------------

    private fun podName(p: ButtonClusterPreset) = getString(
        when (p) {
            ButtonClusterPreset.FIVE_WAY -> R.string.bt_pod_five
            ButtonClusterPreset.MODE_ENT -> R.string.bt_pod_mode
            ButtonClusterPreset.BACK_SET -> R.string.bt_pod_back
        },
    )

    private fun podDesc(p: ButtonClusterPreset) = getString(
        when (p) {
            ButtonClusterPreset.FIVE_WAY -> R.string.bt_pod_five_d
            ButtonClusterPreset.MODE_ENT -> R.string.bt_pod_mode_d
            ButtonClusterPreset.BACK_SET -> R.string.bt_pod_back_d
        },
    )

    private fun gestureName(g: ButtonGesture) = getString(
        when (g) {
            ButtonGesture.NAV_BACK -> R.string.bg_back
            ButtonGesture.NAV_FWD -> R.string.bg_fwd
            ButtonGesture.SELECT_PRESS -> R.string.bg_select
            ButtonGesture.NAV_BACK_LONG -> R.string.bg_back_long
            ButtonGesture.NAV_FWD_LONG -> R.string.bg_fwd_long
            ButtonGesture.SELECT_LONG -> R.string.bg_select_long
            ButtonGesture.NAV_BACK_DOUBLE -> R.string.bg_back_double
            ButtonGesture.NAV_FWD_DOUBLE -> R.string.bg_fwd_double
            ButtonGesture.SELECT_DOUBLE -> R.string.bg_select_double
        },
    )

    private fun gestureHint(g: ButtonGesture) = getString(
        when (g) {
            ButtonGesture.NAV_BACK -> R.string.bgh_back
            ButtonGesture.NAV_FWD -> R.string.bgh_fwd
            ButtonGesture.SELECT_PRESS -> R.string.bgh_select
            ButtonGesture.NAV_BACK_LONG, ButtonGesture.NAV_FWD_LONG, ButtonGesture.SELECT_LONG -> R.string.bgh_long
            else -> R.string.bgh_double
        },
    )

    private fun actionName(a: ButtonAction): String = ButtonText.action(this, a)

    // ---- views ----------------------------------------------------------------------------

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

    private fun card(title: String, desc: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_tile)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) }
        }
        card.addView(TextView(this).apply {
            text = title
            setTextColor(getColor(R.color.text_primary))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = desc
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
        })
        list.addView(card)
        return card
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, ButtonsActivity::class.java))
        }
    }
}
