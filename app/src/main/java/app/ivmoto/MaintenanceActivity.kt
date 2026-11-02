// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/** Service reminders by kilometres: oil, chain, tires… with the rider's own odometer. */
class MaintenanceActivity : ListScreenActivity() {
    override val screenTitle = R.string.home_tile_maint

    override fun build() {
        val km = Odometer.currentKm(this)
        card(getString(R.string.maint_odo_title), getString(R.string.maint_odo_desc, fmt(km))).setOnClickListener { setOdometer(km) }
        note(getString(R.string.maint_note))
        MaintenanceStore.list(this).forEach { task ->
            val left = Maintenance.remainingKm(task, km)
            val (text, color) = when (Maintenance.level(task, km)) {
                MaintLevel.DUE -> getString(R.string.maint_overdue, fmt(-left)) to R.color.status_error
                MaintLevel.SOON -> getString(R.string.maint_left, fmt(left)) to R.color.status_busy
                MaintLevel.OK -> getString(R.string.maint_left, fmt(left)) to R.color.text_secondary
            }
            card(taskName(task), text, color).setOnClickListener { options(task) }
        }
        card(getString(R.string.maint_add), getString(R.string.maint_add_desc)).setOnClickListener { addTask() }
    }

    private fun fmt(km: Int) = String.format(Locale.getDefault(), "%,d", km)

    private fun taskName(t: MaintTask): String = t.name ?: getString(
        when (t.id) {
            "oil" -> R.string.maint_oil
            "chain" -> R.string.maint_chain
            "tires" -> R.string.maint_tires
            "brakes" -> R.string.maint_brakes
            else -> R.string.maint_air
        },
    )

    private fun setOdometer(current: Int) {
        FormDialog.show(this, getString(R.string.maint_odo_title), listOf(FormDialog.Field(getString(R.string.maint_odo_hint), current.toString(), number = true))) { v ->
            v[0].toIntOrNull()?.let {
                Odometer.set(this, it)
                rebuild()
            }
        }
    }

    private fun options(t: MaintTask) {
        MaterialAlertDialogBuilder(this)
            .setTitle(taskName(t))
            .setItems(arrayOf(getString(R.string.maint_done), getString(R.string.maint_interval), getString(R.string.maint_remove))) { _, i ->
                when (i) {
                    0 -> { MaintenanceStore.markDone(this, t.id); rebuild() }
                    1 -> FormDialog.show(this, taskName(t), listOf(FormDialog.Field(getString(R.string.maint_km_hint), t.intervalKm.toString(), number = true))) { v ->
                        v[0].toIntOrNull()?.let { MaintenanceStore.setInterval(this, t.id, it); rebuild() }
                    }
                    else -> { MaintenanceStore.remove(this, t.id); rebuild() }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addTask() {
        FormDialog.show(
            this, getString(R.string.maint_add),
            listOf(FormDialog.Field(getString(R.string.maint_name_hint)), FormDialog.Field(getString(R.string.maint_km_hint), "", number = true)),
        ) { v ->
            val interval = v[1].toIntOrNull()
            if (v[0].isNotEmpty() && interval != null) {
                MaintenanceStore.add(this, v[0], interval)
                rebuild()
            }
        }
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, MaintenanceActivity::class.java))
        }
    }
}
