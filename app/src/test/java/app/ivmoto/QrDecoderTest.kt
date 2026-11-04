package app.ivmoto

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecoderTest {
    private fun qrLuma(text: String, size: Int = 300): ByteArray {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        return ByteArray(size * size) { i -> if (m.get(i % size, i / size)) 0 else 255.toByte() }
    }

    @Test
    fun a_qr_in_a_grey_image_is_read() {
        val text = "WIFI:T:WPA;S:CFMOTO-1234;P:secret123;;"
        assertEquals(text, QrDecoder.decodeLuma(qrLuma(text), 300, 300))
    }

    @Test
    fun an_image_without_a_qr_gives_nothing() {
        assertNull(QrDecoder.decodeLuma(ByteArray(300 * 300) { 200.toByte() }, 300, 300))
    }
}
