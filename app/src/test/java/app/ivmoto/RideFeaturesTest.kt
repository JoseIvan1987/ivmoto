package app.ivmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideFeaturesTest {
    // ---- rest reminder ----
    @Test fun a_rest_is_due_after_the_interval_of_riding() {
        val h = 3_600_000L
        assertTrue(RestReminder.due(movingMs = 2 * h, lastReminderAtMs = 0L, intervalMs = 2 * h))
        assertFalse(RestReminder.due(movingMs = 2 * h - 1, lastReminderAtMs = 0L, intervalMs = 2 * h))
    }

    @Test fun after_a_reminder_the_next_one_waits_a_full_interval() {
        val h = 3_600_000L
        assertFalse(RestReminder.due(movingMs = 3 * h, lastReminderAtMs = 2 * h, intervalMs = 2 * h))
        assertTrue(RestReminder.due(movingMs = 4 * h, lastReminderAtMs = 2 * h, intervalMs = 2 * h))
    }

    // ---- curves ----
    private fun line(vararg p: Pair<Double, Double>) = p.map { GpxPoint(it.first, it.second) }

    @Test fun a_straight_road_has_no_curve() {
        val pts = (0..40).map { GpxPoint(19.0 + it * 0.0005, -99.0) }
        assertNull(CurveAhead.find(pts, 0, aheadM = 600.0))
    }

    @Test fun a_right_angle_turn_ahead_is_found_with_its_distance() {
        // 1 km north, then 1 km east.
        val north = (0..20).map { GpxPoint(19.0 + it * 0.00045, -99.0) }
        val east = (1..20).map { GpxPoint(north.last().lat, -99.0 + it * 0.00048) }
        val c = CurveAhead.find(north + east, 0, aheadM = 1200.0)
        assertNotNull(c)
        assertTrue("distance ${c!!.distanceM}", c.distanceM in 850.0..1050.0)
        assertTrue("angle ${c.turnDeg}", c.turnDeg >= 60.0)
    }

    @Test fun a_curve_beyond_the_look_ahead_is_ignored() {
        val north = (0..20).map { GpxPoint(19.0 + it * 0.00045, -99.0) }
        val east = (1..20).map { GpxPoint(north.last().lat, -99.0 + it * 0.00048) }
        assertNull(CurveAhead.find(north + east, 0, aheadM = 300.0))
    }

    // ---- fuel range ----
    @Test fun range_is_what_the_tank_allows_minus_the_km_since_the_last_fill() {
        assertEquals(180.0, FuelRange.remainingKm(tankLiters = 15.0, kmPerLiter = 20.0, kmSinceFill = 120.0)!!, 0.01)
    }

    @Test fun range_never_goes_below_zero_and_needs_the_data() {
        assertEquals(0.0, FuelRange.remainingKm(15.0, 20.0, 400.0)!!, 0.01)
        assertNull(FuelRange.remainingKm(0.0, 20.0, 10.0))
        assertNull(FuelRange.remainingKm(15.0, 0.0, 10.0))
    }

    // ---- expenses ----
    @Test fun expenses_add_fuel_and_service_and_give_cost_per_km() {
        val fuel = listOf(
            FuelEntry("1", 1_000L, 10.0, 250.0, 1000.0),
            FuelEntry("2", 2_000L, 10.0, 260.0, 1300.0),
        )
        val maint = listOf(MaintLogEntry("oil", "Aceite", 1200, 400.0, 1_500L))
        val s = Expenses.summarize(fuel, maint)
        assertEquals(510.0, s.fuelTotal, 0.01)
        assertEquals(400.0, s.serviceTotal, 0.01)
        assertEquals(910.0, s.total, 0.01)
        assertEquals(910.0 / 300.0, s.costPerKm!!, 0.001)
    }

    @Test fun cost_per_km_needs_two_odometer_readings() {
        val s = Expenses.summarize(listOf(FuelEntry("1", 1L, 10.0, 250.0, 1000.0)), emptyList())
        assertNull(s.costPerKm)
    }

    // ---- weather ----
    private val sample = """[{"latitude":19.0,"longitude":-99.0,"hourly":{"time":["2026-11-03T10:00","2026-11-03T11:00","2026-11-03T12:00"],
        "precipitation_probability":[10,80,30],"wind_speed_10m":[12.0,45.5,20.0],"wind_gusts_10m":[20.0,70.0,30.0]}}]"""

    @Test fun weather_json_is_read_per_point_and_hour() {
        val f = RouteWeather.parse(sample)
        assertEquals(1, f.size)
        assertEquals(3, f[0].hours.size)
        assertEquals(80, f[0].hours[1].rainPct)
        assertEquals(45.5, f[0].hours[1].windKmh, 0.01)
    }

    @Test fun the_summary_picks_the_worst_hour_the_rider_will_be_there() {
        val f = RouteWeather.parse(sample)
        // Rider reaches the point 1 hour after the first forecast hour.
        val s = RouteWeather.summarize(f, etaHoursPerPoint = listOf(1))
        assertEquals(80, s.maxRainPct)
        assertEquals(70.0, s.maxGustKmh, 0.01)
        assertTrue(s.warnRain)
        assertTrue(s.warnWind)
    }

    @Test fun calm_weather_gives_no_warnings() {
        val calm = """[{"hourly":{"time":["2026-11-03T10:00"],"precipitation_probability":[5],"wind_speed_10m":[8.0],"wind_gusts_10m":[12.0]}}]"""
        val s = RouteWeather.summarize(RouteWeather.parse(calm), listOf(0))
        assertFalse(s.warnRain)
        assertFalse(s.warnWind)
    }

    // ---- live location message ----
    @Test fun the_share_text_has_a_maps_link_with_the_position() {
        val t = LiveShare.message(19.4326, -99.1332, "Iván", accuracyM = 12f)
        assertTrue(t.contains("https://maps.google.com/?q=19.4326,-99.1332"))
        assertTrue(t.contains("Iván"))
    }
}
