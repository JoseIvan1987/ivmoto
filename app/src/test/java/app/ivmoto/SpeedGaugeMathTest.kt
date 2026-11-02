package app.ivmoto

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedGaugeMathTest {
    @Test
    fun zero_speed_has_no_sweep() = assertEquals(0f, SpeedGaugeView.sweepFor(0f), 0.001f)

    @Test
    fun hundred_of_two_hundred_is_half_of_270() = assertEquals(135f, SpeedGaugeView.sweepFor(100f), 0.001f)

    @Test
    fun above_max_is_clamped_to_270() = assertEquals(270f, SpeedGaugeView.sweepFor(300f), 0.001f)

    @Test
    fun negative_speed_is_clamped_to_zero() = assertEquals(0f, SpeedGaugeView.sweepFor(-5f), 0.001f)
}
