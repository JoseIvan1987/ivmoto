// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/** Reads QR codes on the phone with ZXing: nothing is sent anywhere. */
object QrDecoder {
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )

    /** [luma] is one byte of brightness per pixel (the Y plane of a camera frame). */
    fun decodeLuma(luma: ByteArray, width: Int, height: Int): String? =
        decode(PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false))

    /** [argb] is a decoded picture, one Int per pixel (a photo or screenshot). */
    fun decodeArgb(argb: IntArray, width: Int, height: Int): String? =
        decode(RGBLuminanceSource(width, height, argb))

    private fun decode(source: LuminanceSource): String? = try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (_: Exception) {
        null
    }
}
