// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.app.Activity
import android.widget.ImageView

/** Fills the shared donation block: a QR and an open button for PayPal and for Mercado Pago. */
object DonateBlock {
    fun bind(activity: Activity) {
        activity.findViewById<ImageView>(R.id.don_pp_qr).setImageBitmap(QrImage.make(AppLinks.DONATE, 720))
        activity.findViewById<ImageView>(R.id.don_mp_qr).setImageBitmap(QrImage.make(AppLinks.DONATE_MP, 720))
        activity.findViewById<android.view.View>(R.id.don_pp_open).setOnClickListener { AppLinks.open(activity, AppLinks.DONATE) }
        activity.findViewById<android.view.View>(R.id.don_mp_open).setOnClickListener { AppLinks.open(activity, AppLinks.DONATE_MP) }
    }
}
