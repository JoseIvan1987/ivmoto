// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** What the bike has cost: fuel and services, and the cost per km. */
class ExpensesActivity : ListScreenActivity() {
    override val screenTitle = R.string.home_tile_expenses

    private fun money(v: Double) = String.format(Locale.getDefault(), "$%.2f", v)

    override fun build() {
        val services = MaintLogStore.list(this)
        val s = Expenses.summarize(FuelStore.list(this), services)
        card(getString(R.string.exp_total, money(s.total)), getString(R.string.exp_total_desc, money(s.fuelTotal), money(s.serviceTotal)), R.color.ivm_cyan)
        val perKm = s.costPerKm
        if (perKm != null) card(getString(R.string.exp_per_km, money(perKm)), getString(R.string.exp_per_km_desc)) else note(getString(R.string.exp_need_more))
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM)
        services.sortedByDescending { it.time }.take(15).forEach {
            card(it.name, date.format(Date(it.time)) + " · " + String.format(Locale.getDefault(), "%,d km", it.km) + " · " + money(it.cost))
        }
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, ExpensesActivity::class.java))
        }
    }
}
