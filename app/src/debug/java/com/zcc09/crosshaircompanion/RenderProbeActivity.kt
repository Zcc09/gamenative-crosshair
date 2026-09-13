package com.zcc09.crosshaircompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

/**
 * Debug-only self-test activity used by the CI E2E harness.
 *
 * The overlay's own view class is rendered into a 1080x2400 bitmap with the
 * active profile's spec and the pixels are asserted (crosshair at screen
 * center, corners untouched). The render is saved to the app's files dir so CI
 * can pull it as evidence.
 *
 * Why a bitmap instead of a screenshot: on Android 12+ screenshots and screen
 * recordings exclude non-trusted overlay windows by design, so an adb
 * screencap can never show the overlay itself.
 */
class RenderProbeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.textSize = 15f
        tv.setPadding(48, 140, 48, 48)
        setContentView(tv)

        val profile = Store.activeProfile(this)
        val spec = profile?.spec ?: CrosshairSpec()

        val w = 1080
        val h = 2400
        val view = CrosshairView(this)
        view.spec = spec
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
        view.draw(canvas)

        val cx = w / 2
        val cy = h / 2
        var center = 0
        for (x in cx - 60 until cx + 60) {
            for (y in cy - 60 until cy + 60) {
                val p = bmp.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r > 230 && b > 230 && g < 80) center++
            }
        }
        val corners = listOf(40 to 40, w - 40 to 40, 40 to h - 40, w - 40 to h - 40)
            .count { (x, y) -> bmp.getPixel(x, y) != 0 }

        val pass = center >= 100 && corners == 0
        val msg = "CROSSHAIR-PROBE " + (if (pass) "PASS" else "FAIL") +
            " profile=${profile?.name} shape=${spec.shape} color=${spec.color} centerPx=$center dirtyCorners=$corners"
        Log.i("CROSSHAIR-PROBE", msg)
        tv.text = msg

        try {
            val f = File(filesDir, "crosshair_probe.png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Log.i("CROSSHAIR-PROBE", "saved " + f.absolutePath + " bytes=" + f.length())
        } catch (e: Exception) {
            Log.i("CROSSHAIR-PROBE", "png save failed: " + e.message)
        }
    }
}
