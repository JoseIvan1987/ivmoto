// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** "Elige tu moto": models grouped by family (450, 500, 675, 800, 1000) plus your own photo. */
class BikePickerActivity : AppCompatActivity() {

    private lateinit var note: TextView
    private lateinit var adapter: CatalogAdapter

    private val photoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val saved = BikeMemory.selected(this) ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        val path = BikePhotos.import(this, uri, saved.raw)
        if (path != null) {
            BikeMemory.setPhoto(this, saved.raw, path)
        } else {
            Toast.makeText(this, R.string.picker_photo_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_bike_picker)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.picker_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        note = findViewById(R.id.picker_note)
        adapter = CatalogAdapter(BikeCatalog.byId(AppSettings.selectedModelId(this))?.id) { picked ->
            AppSettings.setSelectedModelId(this, picked.id)
            updateNote()
        }
        adapter.submit(BikeCatalog.families(), this)
        val layout = GridLayoutManager(this, 2)
        layout.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (adapter.isHeader(position)) 2 else 1
        }
        findViewById<RecyclerView>(R.id.picker_grid).apply {
            layoutManager = layout
            adapter = this@BikePickerActivity.adapter
            isNestedScrollingEnabled = false
        }
        findViewById<EditText>(R.id.picker_search).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                adapter.submit(BikeCatalog.families(s?.toString().orEmpty()), this@BikePickerActivity)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        findViewById<View>(R.id.picker_back).setOnClickListener { finish() }
        findViewById<View>(R.id.picker_save).setOnClickListener { finish() }
        findViewById<View>(R.id.picker_photo).setOnClickListener {
            if (BikeMemory.selected(this) == null) {
                Toast.makeText(this, R.string.picker_need_bike, Toast.LENGTH_LONG).show()
            } else {
                photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        findViewById<View>(R.id.picker_no_model).setOnClickListener { ConnectActivity.start(this) }
        updateNote()
    }

    /** The scanned QR always decides the real hardware profile; tell the rider if it differs. */
    private fun updateNote() {
        val chosen = BikeCatalog.byId(AppSettings.selectedModelId(this))
        val qr = BikeMemory.lastQr(this)
        val detected = if (qr != null) runCatching { BikeProfiles.selectByQr(qr, this) }.getOrNull() else null
        if (chosen?.profile != null && detected != null && detected !== chosen.profile) {
            note.text = getString(R.string.picker_detected, detected.name)
            note.visibility = View.VISIBLE
        } else {
            note.visibility = View.GONE
        }
    }

    private class CatalogAdapter(
        private var selected: String?,
        private val onPick: (CatalogBike) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows: List<Any> = emptyList()

        class BikeVH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.item_name)
            val kind: TextView = v.findViewById(R.id.item_kind)
            val image: ImageView = v.findViewById(R.id.item_image)
        }

        class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.item_header)
        }

        fun submit(groups: List<Pair<String, List<CatalogBike>>>, ctx: Context) {
            rows = groups.flatMap { (family, bikes) -> listOf<Any>(ctx.getString(R.string.catalog_family, family)) + bikes }
            notifyDataSetChanged()
        }

        fun isHeader(position: Int) = rows[position] is String

        override fun getItemViewType(position: Int) = if (isHeader(position)) 0 else 1

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) HeaderVH(inflater.inflate(R.layout.item_catalog_header, parent, false))
            else BikeVH(inflater.inflate(R.layout.item_catalog_bike, parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            if (holder is HeaderVH) {
                holder.title.text = row as String
                return
            }
            holder as BikeVH
            val bike = row as CatalogBike
            holder.name.text = bike.name
            holder.kind.text = bike.kind
            val photo = ModelPhotos.load(holder.itemView.context, bike.id, 400)
            if (photo != null) {
                holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
                holder.image.setImageBitmap(photo)
            } else {
                holder.image.scaleType = ImageView.ScaleType.FIT_CENTER
                holder.image.setImageResource(R.drawable.ic_moto_illustration)
            }
            holder.itemView.setBackgroundResource(if (bike.id == selected) R.drawable.bg_card_selected else R.drawable.bg_tile)
            holder.itemView.setOnClickListener {
                selected = bike.id
                onPick(bike)
                notifyDataSetChanged()
            }
        }
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, BikePickerActivity::class.java))
        }
    }
}
