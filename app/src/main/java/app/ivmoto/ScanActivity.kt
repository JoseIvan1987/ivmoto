// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** IVMoto's QR scanner: point the camera at the pairing QR shown on the bike's dash. */
class ScanActivity : AppCompatActivity() {

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val done = AtomicBoolean(false)
    private lateinit var hint: TextView

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else hint.setText(R.string.scan_need_camera)
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scanPhoto(uri)
    }

    /** Fallback when the camera does not work: read the QR from a photo or screenshot. */
    private fun scanPhoto(uri: Uri) {
        analysisExecutor.execute {
            val raw = runCatching {
                val src = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                val bmp = android.graphics.ImageDecoder.decodeBitmap(src) { dec, _, _ ->
                    dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    dec.setTargetSampleSize(2)
                }
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                QrDecoder.decodeArgb(px, bmp.width, bmp.height)
            }.getOrNull()
            runOnUiThread { if (raw == null) hint.setText(R.string.scan_photo_failed) else onCode(raw) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_scan)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scan_root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        hint = findViewById(R.id.scan_hint)
        findViewById<android.view.View>(R.id.scan_back).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.scan_gallery).setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.scan_preview).surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy ->
                if (done.get()) {
                    proxy.close()
                    return@setAnalyzer
                }
                try {
                    val plane = proxy.planes[0]
                    val w = proxy.width
                    val h = proxy.height
                    val rowStride = plane.rowStride
                    val buf = plane.buffer
                    val luma = ByteArray(w * h)
                    for (y in 0 until h) {
                        buf.position(y * rowStride)
                        buf.get(luma, y * w, w)
                    }
                    QrDecoder.decodeLuma(luma, w, h)?.let { onCode(it) }
                } catch (_: Exception) {
                } finally {
                    proxy.close()
                }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { hint.setText(R.string.scan_camera_failed) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onCode(raw: String) {
        if (QrData.parse(raw) == null) {
            runOnUiThread { hint.setText(R.string.scan_not_a_bike) }
            return
        }
        if (!done.compareAndSet(false, true)) return
        runOnUiThread {
            setResult(RESULT_OK, android.content.Intent().putExtra(RESULT_QR, raw))
            finish()
        }
    }

    override fun onDestroy() {
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val RESULT_QR = "ivmoto_qr"
    }
}
