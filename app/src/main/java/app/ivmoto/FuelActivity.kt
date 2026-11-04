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
        val tank = RideSettings.tankLiters(this)
        card(
            getString(R.string.fuel_tank_card),
            if (tank > 0) getString(R.string.fuel_tank_set, String.format(Locale.getDefault(), "%.1f", tank)) else getString(R.string.fuel_tank_none),
        ).setOnClickListener { editTank(tank) }
        val lastOdo = entries.mapNotNull { it.odometerKm }.maxOrNull()
        if (summary != null && lastOdo != null) {
            FuelRange.remainingKm(tank, summary.kmPerLiter, Odometer.currentKm(this) - lastOdo)?.let { range ->
                card(getString(R.string.fuel_range_title, range.toInt()), getString(R.string.fuel_range_desc),
                    if (range < 60) R.color.status_error else R.color.ivm_cyan)
            }
        }
        card(getString(R.string.fuel_stations), getString(R.string.fuel_stations_desc)).setOnClickListener { MapActivity.startFuel(this) }
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

    private fun editTank(current: Double) {
        FormDialog.show(this, getString(R.string.fuel_tank_card),
            listOf(FormDialog.Field(getString(R.string.fuel_tank_hint), if (current > 0) current.toString() else "", decimal = true))) { v ->
            v[0].toDoubleOrNull()?.takeIf { it > 0 }?.let { RideSettings.setTankLiters(this, it); rebuild() }
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
