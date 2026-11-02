// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** A simple titled screen made of cards; subclasses describe the cards in [build]. */
abstract class ListScreenActivity : AppCompatActivity() {

    protected lateinit var list: LinearLayout

    @get:StringRes
    protected abstract val screenTitle: Int

    protected abstract fun build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        findViewById<TextView>(R.id.settings_title).setText(screenTitle)
        findViewById<View>(R.id.settings_back).setOnClickListener { finish() }
        list = findViewById(R.id.settings_list)
    }

    override fun onResume() {
        super.onResume()
        list.removeAllViews()
        build()
    }

    protected fun rebuild() {
        list.removeAllViews()
        build()
    }

    protected fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    protected fun card(title: String, desc: String, @ColorRes descColor: Int = R.color.text_secondary): LinearLayout {
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
        if (desc.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = desc
                textSize = 13f
                setTextColor(getColor(descColor))
            })
        }
        list.addView(card)
        return card
    }

    protected fun note(text: String) {
        list.addView(TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(dp(4), dp(10), dp(4), 0)
        })
    }
}

/** A small dialog with several text fields. */
object FormDialog {
    data class Field(val hint: String, val value: String = "", val number: Boolean = false, val decimal: Boolean = false)

    fun show(ctx: Context, title: String, fields: List<Field>, onOk: (List<String>) -> Unit) {
        val density = ctx.resources.displayMetrics.density
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (12 * density).toInt(), (24 * density).toInt(), 0)
        }
        val edits = fields.map { f ->
            EditText(ctx).apply {
                hint = f.hint
                setText(f.value)
                setSelection(text.length)
                inputType = when {
                    f.decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    f.number -> InputType.TYPE_CLASS_NUMBER
                    else -> InputType.TYPE_CLASS_TEXT
                }
                box.addView(this)
            }
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk(edits.map { it.text.toString().trim() }) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
