// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.concurrent.thread

/** The screens of an update: offer it, download it with progress, then install. */
object UpdateFlow {

    fun offer(activity: AppCompatActivity, m: UpdateManifest) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.upd_title, m.versionName))
            .setMessage(m.notes.ifBlank { activity.getString(R.string.upd_no_notes) })
            .setPositiveButton(R.string.upd_install) { _, _ -> start(activity, m) }
            .setNegativeButton(R.string.upd_later, null)
            .show()
    }

    /** Manual "check now": tells the rider the result either way. */
    fun checkManually(activity: AppCompatActivity) {
        Toast.makeText(activity, R.string.upd_checking, Toast.LENGTH_SHORT).show()
        thread {
            val result = AppUpdater.checkNow(activity)
            activity.runOnUiThread {
                when (result) {
                    is AppUpdater.Check.Available -> offer(activity, result.manifest)
                    AppUpdater.Check.UpToDate -> Toast.makeText(activity, R.string.upd_up_to_date, Toast.LENGTH_LONG).show()
                    AppUpdater.Check.Failed -> Toast.makeText(activity, R.string.upd_check_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun start(activity: AppCompatActivity, m: UpdateManifest) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.upd_allow_title)
                .setMessage(R.string.upd_allow_msg)
                .setPositiveButton(R.string.upd_allow_open) { _, _ ->
                    runCatching {
                        activity.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)),
                        )
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val label = TextView(activity).apply { setText(R.string.upd_downloading); gravity = Gravity.CENTER }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(label)
            addView(bar)
        }
        val dialog = MaterialAlertDialogBuilder(activity).setView(box).setCancelable(false).show()
        thread {
            val result = AppUpdater.download(activity, m) { p -> activity.runOnUiThread { bar.progress = p } }
            activity.runOnUiThread {
                dialog.dismiss()
                val file = result.file
                if (file != null) {
                    runCatching { AppUpdater.install(activity, file) }
                        .onFailure { Toast.makeText(activity, R.string.upd_install_failed, Toast.LENGTH_LONG).show() }
                } else {
                    val msg = when (result.problem) {
                        AppUpdater.Problem.HASH -> R.string.upd_err_hash
                        AppUpdater.Problem.SIGNATURE, AppUpdater.Problem.MISMATCH -> R.string.upd_err_signature
                        else -> R.string.upd_err_download
                    }
                    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
