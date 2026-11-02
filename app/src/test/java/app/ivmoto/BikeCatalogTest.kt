package app.ivmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BikeCatalogTest {
    @Test
    fun all_ids_are_unique() {
        val ids = BikeCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun byId_returns_null_for_unknown_and_null() {
        assertNull(BikeCatalog.byId("nope"))
        assertNull(BikeCatalog.byId(null))
    }

    @Test
    fun search_is_case_insensitive_over_name_and_kind() {
        val naked = BikeCatalog.search("naked").map { it.id }
        assertTrue(naked.containsAll(listOf("800nk", "800nk_adv", "450nk", "675nk")))
        val mt = BikeCatalog.search("MT").map { it.id }
        assertTrue(mt.containsAll(listOf("800mt", "1000mtx", "450mt")))
    }

    @Test
    fun search_blank_returns_all() {
        assertEquals(BikeCatalog.all, BikeCatalog.search("  "))
    }

    @Test
    fun mt_x_maps_to_portrait_profile() {
        assertSame(Cfdl26PortraitProfile, BikeCatalog.byId("1000mtx")!!.profile)
    }

    @Test
    fun models_with_tbox_are_in_the_catalog() {
        val ids = BikeCatalog.all.map { it.id }.toSet()
        val expected = setOf("500srvoom", "675nk", "675sr", "450sr", "450srs", "450nk", "450mt", "800mt", "800nk")
        assertTrue("missing: ${expected - ids}", ids.containsAll(expected))
    }

    @Test
    fun families_are_grouped_in_order() {
        val groups = BikeCatalog.families()
        assertEquals(listOf("450", "500", "675", "800", "1000"), groups.map { it.first })
        assertTrue(groups.first { it.first == "500" }.second.any { it.id == "500srvoom" })
        assertTrue(groups.first { it.first == "450" }.second.map { it.id }.containsAll(listOf("450sr", "450srs", "450nk", "450mt")))
    }

    @Test
    fun old_675_id_still_resolves_to_the_nk() {
        assertEquals("675nk", BikeCatalog.byId("675")?.id)
    }

    @Test
    fun search_inside_families_drops_empty_groups() {
        val groups = BikeCatalog.families("voom")
        assertEquals(listOf("500"), groups.map { it.first })
    }
}
