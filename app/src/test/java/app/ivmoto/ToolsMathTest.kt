package app.ivmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsMathTest {

    // ---- maintenance ----------------------------------------------------------------------

    @Test
    fun remaining_is_last_done_plus_interval_minus_current() {
        val t = MaintTask("oil", null, 5000, 10000)
        assertEquals(3000, Maintenance.remainingKm(t, 12000))
    }

    @Test
    fun overdue_when_remaining_is_zero_or_negative() {
        val t = MaintTask("oil", null, 5000, 10000)
        assertEquals(MaintLevel.DUE, Maintenance.level(t, 15000))
        assertEquals(MaintLevel.DUE, Maintenance.level(t, 15500))
    }

    @Test
    fun soon_uses_ten_percent_with_a_hundred_km_floor() {
        val oil = MaintTask("oil", null, 5000, 0)
        assertEquals(MaintLevel.SOON, Maintenance.level(oil, 4600)) // 400 left <= 500
        assertEquals(MaintLevel.OK, Maintenance.level(oil, 4400)) // 600 left
        val chain = MaintTask("chain", null, 500, 0)
        assertEquals(MaintLevel.SOON, Maintenance.level(chain, 420)) // 80 left <= 100
        assertEquals(MaintLevel.OK, Maintenance.level(chain, 350)) // 150 left
    }

    @Test
    fun odometer_follows_trip_distance_after_the_rider_sets_it() {
        // rider typed 12,000 km when trips summed 300 km; now trips sum 450 km
        assertEquals(12150.0, Odometer.compute(12000.0, 300.0, 450.0), 0.001)
    }

    @Test
    fun odometer_never_goes_below_the_value_the_rider_set() {
        assertEquals(12000.0, Odometer.compute(12000.0, 300.0, 100.0), 0.001)
    }

    // ---- fuel -----------------------------------------------------------------------------

    private fun entry(odo: Double?, liters: Double, price: Double) = FuelEntry("x$odo", 0L, liters, price, odo)

    @Test
    fun average_consumption_uses_km_between_fills_over_liters_filled() {
        val s = FuelMath.summary(listOf(entry(1000.0, 8.0, 160.0), entry(1300.0, 10.0, 200.0), entry(1620.0, 9.6, 192.0)))
        assertNotNull(s)
        assertEquals((300.0 + 320.0) / (10.0 + 9.6), s!!.kmPerLiter, 0.01)
        assertEquals((200.0 + 192.0) / 620.0, s.costPerKm, 0.001)
    }

    @Test
    fun no_summary_with_fewer_than_two_fills_with_odometer() {
        assertNull(FuelMath.summary(emptyList()))
        assertNull(FuelMath.summary(listOf(entry(1000.0, 8.0, 160.0))))
        assertNull(FuelMath.summary(listOf(entry(null, 8.0, 160.0), entry(null, 9.0, 180.0))))
    }

    @Test
    fun entries_are_ordered_by_odometer_not_by_insertion() {
        val s = FuelMath.summary(listOf(entry(1300.0, 10.0, 200.0), entry(1000.0, 8.0, 160.0)))
        assertEquals(30.0, s!!.kmPerLiter, 0.01)
    }

    // ---- speed alert ----------------------------------------------------------------------

    @Test
    fun alert_only_when_enabled_above_limit_and_after_cooldown() {
        assertTrue(SpeedAlert.shouldAlert(true, 100, 120, 100_000L, 0L))
        assertFalse(SpeedAlert.shouldAlert(false, 100, 120, 100_000L, 0L))
        assertFalse(SpeedAlert.shouldAlert(true, 100, 90, 100_000L, 0L))
        assertFalse(SpeedAlert.shouldAlert(true, 100, 120, 100_000L, 90_000L)) // 10 s since the last one
        assertTrue(SpeedAlert.shouldAlert(true, 100, 120, 100_000L, 80_000L)) // 20 s since the last one
    }
}
