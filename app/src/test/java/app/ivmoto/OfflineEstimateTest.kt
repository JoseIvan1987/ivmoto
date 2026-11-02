package app.ivmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineEstimateTest {
    @Test
    fun the_whole_world_at_zoom_zero_is_one_tile() {
        assertEquals(1, OfflineEstimate.tileCount(85.0, -85.0, 179.9, -179.9, 0, 0))
    }

    @Test
    fun zoom_zero_to_one_adds_four_more_tiles() {
        assertEquals(5, OfflineEstimate.tileCount(85.0, -85.0, 179.9, -179.9, 0, 1))
    }

    @Test
    fun a_tiny_area_needs_one_tile_per_zoom_level() {
        assertEquals(11, OfflineEstimate.tileCount(10.3005, 10.3, 20.3005, 20.3, 0, 10))
    }

    @Test
    fun bigger_detail_means_more_tiles() {
        val low = OfflineEstimate.tileCount(19.5, 19.3, -99.0, -99.3, 6, 12)
        val high = OfflineEstimate.tileCount(19.5, 19.3, -99.0, -99.3, 6, 15)
        assertTrue(high > low)
    }

    @Test
    fun megabytes_is_at_least_one_and_grows_with_tiles() {
        assertEquals(1, OfflineEstimate.megabytes(3))
        assertTrue(OfflineEstimate.megabytes(10_000) > OfflineEstimate.megabytes(1_000))
    }
}
