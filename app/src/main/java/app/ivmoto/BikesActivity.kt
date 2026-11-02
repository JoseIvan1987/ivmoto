// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** The bikes you have paired: pick the active one, rename it, remove it, or pair another. */
class BikesActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout

    private val scan = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val raw = r.data?.getStringExtra(ScanActivity.RESULT_QR)
        if (r.resultCode != RESULT_OK || raw == null) return@registerForActivityResult
        val qr = QrData.parse(raw) ?: return@registerForActivityResult
        BikeMemory.save(this, raw, qr)
        build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        findViewById<TextView>(R.id.settings_title).setText(R.string.st_garage)
        findViewById<View>(R.id.settings_back).setOnClickListener { finish() }
        list = findViewById(R.id.settings_list)
        build()
    }

    private fun build() {
        list.removeAllViews()
        val bikes = BikeMemory.devices(this)
        val active = BikeMemory.selected(this)?.raw
        if (bikes.isEmpty()) {
            card(getString(R.string.bikes_empty), getString(R.string.bikes_empty_desc), false)
        }
        bikes.forEach { bike ->
            val isActive = bike.raw == active
            val c = card(bike.name, getString(if (isActive) R.string.bikes_active else R.string.bikes_tap), isActive)
            c.setOnClickListener { options(bike) }
        }
        card(getString(R.string.bikes_add), getString(R.string.bikes_add_desc), false).setOnClickListener {
            scan.launch(Intent(this, ScanActivity::class.java))
        }
    }

    private fun options(bike: SavedBike) {
        val items = arrayOf(
            getString(R.string.bikes_use),
            getString(R.string.bikes_rename),
            getString(R.string.bikes_remove),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(bike.name)
            .setItems(items) { _, i ->
                when (i) {
                    0 -> { BikeMemory.select(this, bike.raw); build() }
                    1 -> rename(bike)
                    else -> confirmRemove(bike)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun rename(bike: SavedBike) {
        val input = EditText(this).apply {
            setText(bike.name)
            setSelection(text.length)
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bikes_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    BikeMemory.rename(this, bike.raw, name)
                    build()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRemove(bike: SavedBike) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.bikes_remove_ask, bike.name))
            .setPositiveButton(R.string.bikes_remove) { _, _ ->
                BikeMemory.remove(this, bike.raw)
                build()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun card(title: String, desc: String, selected: Boolean): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(if (selected) R.drawable.bg_card_selected else R.drawable.bg_tile)
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
            ctx.startActivity(Intent(ctx, BikesActivity::class.java))
        }
    }
}
