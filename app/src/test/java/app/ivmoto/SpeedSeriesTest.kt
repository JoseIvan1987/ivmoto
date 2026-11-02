package app.ivmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedSeriesTest {
    @Test
    fun resample_averages_each_bucket() {
        assertEquals(listOf(15f, 35f), SpeedSeries.resample(listOf(10f, 20f, 30f, 40f), 2))
    }

    @Test
    fun resample_keeps_short_series_as_they_are() {
        assertEquals(listOf(10f, 20f), SpeedSeries.resample(listOf(10f, 20f), 5))
    }

    @Test
    fun resample_of_nothing_is_empty() {
        assertTrue(SpeedSeries.resample(emptyList(), 10).isEmpty())
    }

    @Test
    fun resample_handles_uneven_buckets_without_losing_samples() {
        val out = SpeedSeries.resample(listOf(1f, 2f, 3f, 4f, 5f), 2)
        assertEquals(2, out.size)
        assertEquals((1f + 2f) / 2f, out[0], 0.001f)
        assertEquals((3f + 4f + 5f) / 3f, out[1], 0.001f)
    }

    @Test
    fun speeds_convert_ms_to_kmh_and_never_go_negative() {
        val pts = listOf(TrackPoint(0.0, 0.0, 0L, 10f), TrackPoint(0.0, 0.0, 1L, -1f))
        assertEquals(listOf(36f, 0f), SpeedSeries.kmh(pts))
    }
}
