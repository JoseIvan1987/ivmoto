// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.TextView

/** Outside links the app offers, always opened by an explicit tap. */
object AppLinks {
    const val IVSOFTWARE = "https://ivsoftware.pages.dev/"
    const val REPO = "https://github.com/JoseIvan1987/ivmoto"
    const val ORIGINAL = "https://github.com/zanderp/open-cfmoto"
    /** The original project's own donation page (taken from its README). */
    const val ORIGINAL_DONATE = "https://ko-fi.com/alexandrupopa"

    fun open(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Turns a "Created by IVSoftware" label into a link to the IVSoftware site. */
    fun linkCredit(label: TextView) {
        label.setTextColor(label.context.getColor(R.color.ivm_cyan))
        label.setOnClickListener(View.OnClickListener { open(label.context, IVSOFTWARE) })
    }
}
