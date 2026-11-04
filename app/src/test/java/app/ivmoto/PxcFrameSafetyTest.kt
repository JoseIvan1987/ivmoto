package app.ivmoto

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PxcFrameSafetyTest {
    private fun header(cmd: Int, totalLen: Int, magic: Int = cmd xor totalLen): ByteArray =
        ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(cmd).putInt(totalLen).putInt(magic).putInt(0).array()

    @Test
    fun a_huge_declared_length_is_refused_instead_of_allocating_it() {
        // 2 GB declared; the "magic" is trivial to forge, so a rogue peer can send this.
        try {
            PxcFrame.read(ByteArrayInputStream(header(0x20, Int.MAX_VALUE - 8)))
            fail("expected the frame to be refused")
        } catch (e: IllegalStateException) {
            // refused cleanly
        }
    }

    @Test
    fun a_normal_frame_is_still_read() {
        val payload = ByteArray(10) { it.toByte() }
        val f = PxcFrame.read(ByteArrayInputStream(header(0x20, 16 + payload.size) + payload))
        assertEquals(10, f!!.payload.size)
    }
}
