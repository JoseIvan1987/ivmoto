// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Plain-language diagnostics: read, copy or share the connection log when something goes wrong. */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var body: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_diagnostics)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.diag_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        body = findViewById(R.id.diag_body)
        scroll = findViewById(R.id.diag_scroll)
        findViewById<View>(R.id.diag_back).setOnClickListener { finish() }
        findViewById<View>(R.id.diag_copy).setOnClickListener { copy() }
        findViewById<View>(R.id.diag_share).setOnClickListener { share() }
        findViewById<View>(R.id.diag_clear).setOnClickListener {
            LogBus.clear()
            refresh()
        }
        LogBus.listener = { runOnUiThread { refresh() } }
        refresh()
    }

    override fun onDestroy() {
        LogBus.listener = null
        super.onDestroy()
    }

    private fun refresh() {
        val text = LogBus.snapshot()
        body.text = if (text.isBlank()) getString(R.string.diag_empty) else text
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun copy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("IVMoto", LogBus.snapshot()))
        Toast.makeText(this, R.string.diag_copied, Toast.LENGTH_SHORT).show()
    }

    private fun share() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "IVMoto diagnostics")
            .putExtra(Intent.EXTRA_TEXT, LogBus.snapshot())
        runCatching { startActivity(Intent.createChooser(send, getString(R.string.diag_share))) }
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, DiagnosticsActivity::class.java))
        }
    }
}
