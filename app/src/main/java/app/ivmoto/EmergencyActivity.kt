// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale

/**
 * Emergency tools: an emergency contact, a one-tap message with your location, sharing your location
 * with any app, and the emergency number. Nothing is sent by IVMoto: the rider's own message app opens
 * with the text ready and the rider taps send.
 */
class EmergencyActivity : ListScreenActivity() {
    override val screenTitle = R.string.home_tile_sos

    private var pendingAction: (() -> Unit)? = null

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pendingAction?.invoke()
        pendingAction = null
    }

    private fun prefs() = getSharedPreferences("ivmoto_emergency", MODE_PRIVATE)
    private fun contactName() = prefs().getString("name", "").orEmpty()
    private fun contactPhone() = prefs().getString("phone", "").orEmpty()

    override fun build() {
        val name = contactName()
        val phone = contactPhone()
        val hasContact = phone.isNotBlank()
        card(
            getString(R.string.sos_contact),
            if (hasContact) listOf(name, phone).filter { it.isNotBlank() }.joinToString(" · ") else getString(R.string.sos_contact_none),
        ).setOnClickListener { editContact() }

        card(getString(R.string.sos_notify), getString(R.string.sos_notify_desc)).setOnClickListener {
            if (!hasContact) {
                Toast.makeText(this, R.string.sos_no_contact, Toast.LENGTH_LONG).show()
            } else {
                withLocation { link ->
                    val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)))
                        .putExtra("sms_body", getString(R.string.sos_message, link))
                    runCatching { startActivity(sms) }
                }
            }
        }
        card(getString(R.string.sos_share), getString(R.string.sos_share_desc)).setOnClickListener {
            withLocation { link ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, getString(R.string.sos_share_message, link))
                runCatching { startActivity(Intent.createChooser(send, getString(R.string.sos_share))) }
            }
        }
        val number = emergencyNumber()
        card(getString(R.string.sos_call, number), getString(R.string.sos_call_desc), R.color.status_error).setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
        }
    }

    private fun editContact() {
        FormDialog.show(
            this, getString(R.string.sos_contact),
            listOf(FormDialog.Field(getString(R.string.sos_name_hint), contactName()), FormDialog.Field(getString(R.string.sos_phone_hint), contactPhone(), number = true)),
        ) { v ->
            prefs().edit().putString("name", v[0]).putString("phone", v[1]).apply()
            rebuild()
        }
    }

    /** Runs [action] with a maps link to the rider's last known position, asking for permission if needed. */
    private fun withLocation(action: (String) -> Unit) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingAction = { withLocation(action) }
            askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        val loc = lastLocation()
        if (loc == null) {
            Toast.makeText(this, R.string.sos_no_location, Toast.LENGTH_LONG).show()
            return
        }
        action("https://maps.google.com/?q=${loc.latitude},${loc.longitude}")
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation(): Location? {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun emergencyNumber(): String = when (Locale.getDefault().country) {
        "MX", "US", "CA", "PR" -> "911"
        else -> "112"
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, EmergencyActivity::class.java))
        }
    }
}
