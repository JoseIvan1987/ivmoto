// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Step-by-step Android Auto preparation. Without it Google's app refuses to hand its picture to the
 * dash, which looks like "stuck loading" on the bike.
 */
class PrepareActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_prepare)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.prepare_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        listOf(
            Triple(R.id.g1, R.string.guide_s1, R.string.guide_s1d),
            Triple(R.id.g2, R.string.guide_s2, R.string.guide_s2d),
            Triple(R.id.g3, R.string.guide_s3, R.string.guide_s3d),
            Triple(R.id.g4, R.string.guide_s4, R.string.guide_s4d),
            Triple(R.id.g5, R.string.guide_s5, R.string.guide_s5d),
        ).forEach { (id, title, desc) ->
            val row = findViewById<View>(id)
            row.findViewById<TextView>(R.id.step_title).setText(title)
            row.findViewById<TextView>(R.id.step_desc).setText(desc)
        }
        findViewById<View>(R.id.prepare_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.prepare_open).setOnClickListener {
            val i = packageManager.getLaunchIntentForPackage(ConnectFlow.ANDROID_AUTO_PKG)
            if (i != null) runCatching { startActivity(i) }
        }
        findViewById<View>(R.id.prepare_done).setOnClickListener {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        private const val PREFS = "ivmoto_prepare"
        private const val KEY_DONE = "aa_guide_done"

        fun isDone(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)
    }
}
