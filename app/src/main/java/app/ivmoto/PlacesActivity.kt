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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Saved places: tap one to see the route on the map; hold for rename and delete. */
class PlacesActivity : AppCompatActivity() {

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
        findViewById<TextView>(R.id.settings_title).setText(R.string.home_tile_places)
        findViewById<View>(R.id.settings_back).setOnClickListener { finish() }
        list = findViewById(R.id.settings_list)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        list.removeAllViews()
        val places = PlacesStore.list(this)
        if (places.isEmpty()) {
            card(getString(R.string.places_empty), getString(R.string.places_empty_desc))
            return
        }
        places.forEach { p ->
            val c = card(p.name, getString(R.string.places_tap))
            c.setOnClickListener { MapActivity.startTo(this, p.name, p.lat, p.lon) }
            c.setOnLongClickListener { options(p); true }
        }
    }

    private fun options(p: Place) {
        MaterialAlertDialogBuilder(this)
            .setTitle(p.name)
            .setItems(arrayOf(getString(R.string.places_go), getString(R.string.bikes_rename), getString(R.string.bikes_remove))) { _, i ->
                when (i) {
                    0 -> MapActivity.startTo(this, p.name, p.lat, p.lon)
                    1 -> rename(p)
                    else -> {
                        PlacesStore.remove(this, p.id)
                        build()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun rename(p: Place) {
        val input = EditText(this).apply {
            setText(p.name)
            setSelection(text.length)
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bikes_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                PlacesStore.rename(this, p.id, input.text.toString())
                build()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = desc
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
        })
        list.addView(card)
        return card
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, PlacesActivity::class.java))
        }
    }
}
