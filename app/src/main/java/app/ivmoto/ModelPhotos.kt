// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException

/**
 * Optional catalog photos: drop `<modelId>.webp|jpg|jpeg|png` into `app/src/main/assets/models/`
 * (ids come from [BikeCatalog]). Without a file the screens fall back to the illustration.
 */
object ModelPhotos {
    private val EXTENSIONS = listOf("webp", "jpg", "jpeg", "png")

    fun load(ctx: Context, modelId: String?, maxPx: Int = 900): Bitmap? {
        if (modelId == null) return null
        for (ext in EXTENSIONS) {
            val path = "models/$modelId.$ext"
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                var mx = maxOf(bounds.outWidth, bounds.outHeight)
                while (mx > maxPx * 2) { sample *= 2; mx /= 2 }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                ctx.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }?.let { return it }
            } catch (_: IOException) {
                // not present with this extension; try the next one
            }
        }
        return null
    }
}
