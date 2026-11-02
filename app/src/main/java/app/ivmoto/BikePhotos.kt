// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** Imports a rider's own bike photo into app-private storage and loads it back safely. */
object BikePhotos {
    private const val MAX_IMPORT_PX = 1280

    private fun dir(ctx: Context): File = File(ctx.filesDir, "bike_photos").apply { mkdirs() }

    /** Copies [uri] (downscaled to ≤1280px) into private storage keyed by [key]; null on failure. */
    fun import(ctx: Context, uri: Uri, key: String): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_IMPORT_PX)
        }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val out = File(dir(ctx), BikeScope.idFor(key) + ".jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        out.absolutePath
    } catch (_: Exception) {
        null
    }

    /** Decodes [path] at roughly [maxPx]; null when the path is null, missing or undecodable. */
    fun load(path: String?, maxPx: Int): Bitmap? {
        if (path == null || !File(path).exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPx)
            }
            BitmapFactory.decodeFile(path, opts)
        }.getOrNull()
    }

    private fun sampleSizeFor(w: Int, h: Int, target: Int): Int {
        var sample = 1
        var mx = maxOf(w, h)
        while (mx > target * 2) { sample *= 2; mx /= 2 }
        return sample
    }
}
