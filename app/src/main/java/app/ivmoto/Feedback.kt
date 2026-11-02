// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

/**
 * Suggestions and problem reports. They open the rider's own mail/message app with the text already
 * filled in, so nothing leaves the phone until the rider reviews it and taps send.
 */
object Feedback {
    /** Where reports go. Empty = the rider picks the recipient in the share sheet. */
    const val SUPPORT_EMAIL = "vans.punk@gmail.com"

    fun suggestion(ctx: Context) =
        send(ctx, ctx.getString(R.string.fb_suggestion_subject), header(ctx) + "\n\n" + ctx.getString(R.string.fb_suggestion_body) + "\n\n")

    fun problem(ctx: Context) {
        val log = LogBus.snapshot().takeLast(6_000)
        send(
            ctx,
            ctx.getString(R.string.fb_problem_subject),
            header(ctx) + "\n\n" + ctx.getString(R.string.fb_problem_body) + "\n\n\n--- Diagnóstico ---\n" + log,
        )
    }

    private fun header(ctx: Context): String {
        val model = BikeCatalog.byId(AppSettings.selectedModelId(ctx))?.name ?: "-"
        return "IVMoto ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · moto: $model"
    }

    private fun send(ctx: Context, subject: String, body: String) {
        val intent = if (SUPPORT_EMAIL.isNotBlank()) {
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        } else {
            Intent(Intent.ACTION_SEND).setType("text/plain")
        }.putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)
        try {
            ctx.startActivity(Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(ctx, R.string.fb_no_app, Toast.LENGTH_LONG).show()
        }
    }
}
