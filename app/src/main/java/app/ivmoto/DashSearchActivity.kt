// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * The bike screen has no keyboard, so when the rider taps the magnifier on the dash this screen
 * opens on the phone. What is typed here is sent live to the dash search.
 */
class DashSearchActivity : AppCompatActivity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var input: EditText
    private val send = Runnable { submit() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
            setPadding(dp(20), dp(28), dp(20), dp(20))
            fitsSystemWindows = true
        }
        root.addView(TextView(this).apply {
            setText(R.string.dsa_title)
            textSize = 22f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        input = EditText(this).apply {
            setHint(R.string.dsa_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine()
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            setBackgroundResource(R.drawable.bg_search_bar)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            textSize = 18f
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(dp(4), dp(14), dp(4), 0)
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            setText(R.string.dsa_done)
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_tile)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        setContentView(root)

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                main.removeCallbacks(send)
                main.postDelayed(send, 450)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { main.removeCallbacks(send); submit(); true } else false
        }
        renderStatus()
        input.requestFocus()
        input.post { (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun submit() {
        val q = input.text?.toString().orEmpty()
        if (q.isBlank()) return
        DashRemote.submit(q)
        renderStatus()
    }

    private fun renderStatus() {
        status.setText(if (DashRemote.isAvailable) R.string.dsa_note else R.string.dsa_none)
    }

    override fun onDestroy() {
        main.removeCallbacks(send)
        super.onDestroy()
    }

    companion object {
        fun open(ctx: Context) {
            ctx.startActivity(Intent(ctx, DashSearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }
}
