// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Licence, thanks to the original project, the source code, and what the app is built on. */
class CreditsActivity : ListScreenActivity() {
    override val screenTitle = R.string.credits_title

    override fun build() {
        card(getString(R.string.credits_thanks_title), getString(R.string.credits_thanks), R.color.text_primary)
        card(getString(R.string.credits_orig_title), AppLinks.ORIGINAL.removePrefix("https://"), R.color.ivm_cyan)
            .setOnClickListener { AppLinks.open(this, AppLinks.ORIGINAL) }
        card(getString(R.string.credits_donate_title), getString(R.string.credits_donate_desc), R.color.ivm_cyan)
            .setOnClickListener { AppLinks.open(this, AppLinks.ORIGINAL_DONATE) }
        card(getString(R.string.credits_repo_title), AppLinks.REPO.removePrefix("https://"), R.color.ivm_cyan)
            .setOnClickListener { AppLinks.open(this, AppLinks.REPO) }
        card(getString(R.string.credits_license_title), getString(R.string.credits_license_desc))
            .setOnClickListener { showLicense() }
        card(getString(R.string.credits_lineage_title), getString(R.string.credits_lineage_desc))
        card(getString(R.string.credits_apis_title), getString(R.string.credits_apis_desc))
        card(getString(R.string.credits_libs_title), getString(R.string.credits_libs_desc))
        note(getString(R.string.credits_note))
    }

    private fun showLicense() {
        val text = resources.openRawResource(R.raw.license_agpl).bufferedReader().use { it.readText() }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.credits_license_title)
            .setMessage(text)
            .setPositiveButton(R.string.credits_license_close, null)
            .show()
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, CreditsActivity::class.java))
        }
    }
}
