// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

/**
 * A CFMoto model the rider can pick on "Mi moto". [profile] is the hardware profile we *expect* for
 * the model; the scanned pairing QR always decides the real profile, the catalog only personalizes.
 * [family] groups models in the picker (450, 500, 675, 800, 1000).
 */
data class CatalogBike(
    val id: String,
    val name: String,
    val kind: String,
    val profile: BikeProfile?,
    val family: String,
)

object BikeCatalog {
    /** Models that ship with the T-Box (MotoPlay), grouped by family. */
    val all: List<CatalogBike> = listOf(
        CatalogBike("450sr", "450SR", "Sport", null, "450"),
        CatalogBike("450srs", "450SR‑S", "Sport", null, "450"),
        CatalogBike("450nk", "450NK", "Naked", null, "450"),
        CatalogBike("450mt", "450MT", "Adventure", null, "450"),
        CatalogBike("450clc", "450 CL‑C", "Cruiser", ClC450Profile, "450"),
        CatalogBike("500srvoom", "500SR VOOM", "Sport", null, "500"),
        CatalogBike("675nk", "675NK", "Naked", LegacyCfdl16Profile, "675"),
        CatalogBike("675sr", "675SR", "Sport", LegacyCfdl16Profile, "675"),
        CatalogBike("800nk", "800NK", "Naked", Nk800Profile, "800"),
        CatalogBike("800nk_adv", "800NK Advanced", "Naked táctil", Cfdl26NkTouchProfile, "800"),
        CatalogBike("800mt", "800MT", "Touring", Cfdl26LandscapeProfile, "800"),
        CatalogBike("1000mtx", "1000 MT‑X", "Adventure", Cfdl26PortraitProfile, "1000"),
    )

    private val FAMILY_ORDER = listOf("450", "500", "675", "800", "1000")

    /** Ids saved by older versions. */
    private val ALIASES = mapOf("675" to "675nk")

    fun byId(id: String?): CatalogBike? {
        if (id == null) return null
        val real = ALIASES[id] ?: id
        return all.firstOrNull { it.id == real }
    }

    fun search(query: String): List<CatalogBike> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter { it.name.contains(q, ignoreCase = true) || it.kind.contains(q, ignoreCase = true) }
    }

    /** Models grouped by family in display order, after filtering by [query]; empty groups are dropped. */
    fun families(query: String = ""): List<Pair<String, List<CatalogBike>>> {
        val found = search(query)
        return FAMILY_ORDER.mapNotNull { fam ->
            val items = found.filter { it.family == fam }
            if (items.isEmpty()) null else fam to items
        }
    }
}
