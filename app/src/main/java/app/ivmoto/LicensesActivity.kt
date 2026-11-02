// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** About IVMoto: version, the donation QR, a way to send suggestions or report a problem, and the credit. */
class LicensesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_licenses)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.licenses_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        findViewById<TextView>(R.id.licenses_version).text =
            getString(R.string.about_version, BuildConfig.VERSION_NAME)
        findViewById<android.view.View>(R.id.licenses_back).setOnClickListener { finish() }
        DonateBlock.bind(this)
        findViewById<android.view.View>(R.id.about_suggest).setOnClickListener { Feedback.suggestion(this) }
        findViewById<android.view.View>(R.id.about_report).setOnClickListener { Feedback.problem(this) }
        AppLinks.linkCredit(findViewById(R.id.about_created_by))
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, LicensesActivity::class.java))
        }
    }
}
