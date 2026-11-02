// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Fuel log: every fill-up, and the consumption and cost per km worked out from them. */
class FuelActivity : ListScreenActivity() {
    override val screenTitle = R.string.home_tile_fuel

    override fun build() {
        val entries = FuelStore.list(this).sortedByDescending { it.time }
        val summary = FuelMath.summary(entries)
        if (summary != null) {
            card(
                getString(R.string.fuel_summary_title, String.format(Locale.getDefault(), "%.1f", summary.kmPerLiter)),
                getString(R.string.fuel_summary_desc, String.format(Locale.getDefault(), "$%.2f", summary.costPerKm), entries.size),
                R.color.ivm_cyan,
            )
        } else {
            card(getString(R.string.home_tile_fuel), getString(R.string.fuel_need_more))
        }
        card(getString(R.string.fuel_add), getString(R.string.fuel_add_desc)).setOnClickListener { addEntry() }
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM)
        entries.forEach { e ->
            val title = getString(
                R.string.fuel_entry_title,
                String.format(Locale.getDefault(), "%.2f", e.liters),
                String.format(Locale.getDefault(), "$%.2f", e.price),
            )
            val odo = e.odometerKm?.let { String.format(Locale.getDefault(), "%,.0f km", it) } ?: "—"
            card(title, date.format(Date(e.time)) + " · " + odo).setOnClickListener { confirmRemove(e) }
        }
    }

    private fun addEntry() {
        val odo = Odometer.currentKm(this).takeIf { it > 0 }?.toString().orEmpty()
        FormDialog.show(
            this, getString(R.string.fuel_add),
            listOf(
                FormDialog.Field(getString(R.string.fuel_liters_hint), "", decimal = true),
                FormDialog.Field(getString(R.string.fuel_price_hint), "", decimal = true),
                FormDialog.Field(getString(R.string.maint_odo_hint), odo, decimal = true),
            ),
        ) { v ->
            val liters = v[0].toDoubleOrNull()
            val price = v[1].toDoubleOrNull()
            if (liters != null && liters > 0 && price != null) {
                FuelStore.add(this, liters, price, v[2].toDoubleOrNull())
                rebuild()
            }
        }
    }

    private fun confirmRemove(e: FuelEntry) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.fuel_remove_ask)
            .setPositiveButton(R.string.maint_remove) { _, _ ->
                FuelStore.remove(this, e.id)
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, FuelActivity::class.java))
        }
    }
}
