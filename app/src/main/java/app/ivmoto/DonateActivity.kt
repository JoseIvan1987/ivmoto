// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Support IVMoto: scan the QR or open the link for PayPal or Mercado Pago. */
class DonateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_donate)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.donate_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        findViewById<View>(R.id.donate_back).setOnClickListener { finish() }
        AppLinks.linkCredit(findViewById(R.id.donate_created_by))
        DonateBlock.bind(this)
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, DonateActivity::class.java))
        }
    }
}
