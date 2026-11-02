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
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Interactive handlebar: a virtual control pod. In "Configure" a tap picks what the button does; in
 * "Test" a press runs that function for real when the bike is connected, or just simulates it.
 */
class ControllerActivity : AppCompatActivity() {

    private var configuring = true
    private lateinit var columns: LinearLayout
    private lateinit var feedback: TextView
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var modeConfig: TextView
    private lateinit var modeTest: TextView

    private class Col(val glyph: String, val tap: ButtonGesture, val double: ButtonGesture, val hold: ButtonGesture)

    private fun glyphs(): Triple<String, String, String> = when (PodChoice.current(this) ?: PodChoice.recommended(BikeProfileHolder.active)) {
        ButtonClusterPreset.BACK_SET -> Triple("▲", "▼", "SET")
        ButtonClusterPreset.MODE_ENT -> Triple("▲", "▼", "ENT")
        else -> Triple("◀", "▶", "★")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_controller)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.pad_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        columns = findViewById(R.id.pad_columns)
        feedback = findViewById(R.id.pad_feedback)
        status = findViewById(R.id.pad_status)
        hint = findViewById(R.id.pad_hint)
        modeConfig = findViewById(R.id.pad_mode_config)
        modeTest = findViewById(R.id.pad_mode_test)
        findViewById<View>(R.id.pad_back).setOnClickListener { finish() }
        modeConfig.setOnClickListener { setMode(true) }
        modeTest.setOnClickListener { setMode(false) }
        setMode(true)
    }

    private fun setMode(config: Boolean) {
        configuring = config
        modeConfig.setBackgroundResource(if (config) R.drawable.bg_card_selected else R.drawable.bg_tile)
        modeTest.setBackgroundResource(if (config) R.drawable.bg_tile else R.drawable.bg_card_selected)
        hint.setText(if (config) R.string.pad_hint_config else R.string.pad_hint_test)
        feedback.setText(R.string.pad_feedback_idle)
        status.text = ""
        buildPad()
    }

    private fun buildPad() {
        columns.removeAllViews()
        val (a, b, c) = glyphs()
        listOf(
            Col(a, ButtonGesture.NAV_BACK, ButtonGesture.NAV_BACK_DOUBLE, ButtonGesture.NAV_BACK_LONG),
            Col(b, ButtonGesture.NAV_FWD, ButtonGesture.NAV_FWD_DOUBLE, ButtonGesture.NAV_FWD_LONG),
            Col(c, ButtonGesture.SELECT_PRESS, ButtonGesture.SELECT_DOUBLE, ButtonGesture.SELECT_LONG),
        ).forEach { col -> columns.addView(column(col)) }
    }

    private fun column(col: Col): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        box.addView(padButton(col.glyph, col.tap, big = true))
        box.addView(caption(col.tap))
        box.addView(padButton(getString(R.string.pad_double), col.double, big = false))
        box.addView(caption(col.double))
        box.addView(padButton(getString(R.string.pad_hold), col.hold, big = false))
        box.addView(caption(col.hold))
        return box
    }

    private fun padButton(label: String, gesture: ButtonGesture, big: Boolean): TextView {
        val size = dp(if (big) 84 else 0)
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_primary))
            textSize = if (big) 28f else 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(if (big) R.drawable.bg_pad_button else R.drawable.bg_pad_chip)
            layoutParams = if (big) {
                LinearLayout.LayoutParams(size, size).apply { topMargin = dp(8) }
            } else {
                LinearLayout.LayoutParams(dp(84), dp(40)).apply { topMargin = dp(14) }
            }
            setOnClickListener { press(this, gesture) }
        }
    }

    private fun caption(gesture: ButtonGesture): TextView = TextView(this).apply {
        text = ButtonText.action(this@ControllerActivity, ButtonMap.get(this@ControllerActivity, gesture))
        gravity = Gravity.CENTER
        textSize = 11f
        maxLines = 2
        setTextColor(getColor(R.color.text_secondary))
        setPadding(dp(4), dp(4), dp(4), 0)
        tag = gesture
    }

    private fun press(view: View, gesture: ButtonGesture) {
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
        }.start()
        if (configuring) {
            pickAction(gesture)
            return
        }
        val action = ButtonMap.get(this, gesture)
        val live = ConnectionState.phase == Phase.STREAMING || ConnectionState.phase == Phase.MIRRORING
        feedback.text = ButtonText.action(this, action)
        feedback.alpha = 0f
        feedback.animate().alpha(1f).setDuration(160).start()
        if (live && ButtonMode.isControlAa(this)) {
            MediaButtonBridge.injectGesture(gesture)
            status.setText(R.string.pad_live)
        } else {
            status.setText(R.string.pad_sim)
        }
    }

    private fun pickAction(gesture: ButtonGesture) {
        val actions = ButtonAction.entries
        MaterialAlertDialogBuilder(this)
            .setTitle(ButtonText.action(this, ButtonMap.get(this, gesture)))
            .setItems(actions.map { ButtonText.action(this, it) }.toTypedArray()) { _, i ->
                ButtonMap.set(this, gesture, actions[i])
                refreshCaptions()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshCaptions() {
        fun walk(v: View) {
            val g = v.tag as? ButtonGesture
            if (g != null && v is TextView) v.text = ButtonText.action(this, ButtonMap.get(this, g))
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(columns)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, ControllerActivity::class.java))
        }
    }
}
