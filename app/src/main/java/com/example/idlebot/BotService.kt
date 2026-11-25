package com.example.idlebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class BotService : AccessibilityService() {

    // ---- TUNE THESE (positions are fractions of screen width/height) ----
    private val MANAGE_OR_START = 0.50f to 0.843f   // MANAGE button and calendar START share this spot
    private val CONTINUE        = 0.55f to 0.955f   // "Continue" / "Tap to Continue" at the bottom
    private val AUTO_ASSIGN     = 0.26f to 0.958f
    private val LEVEL_START     = 0.74f to 0.958f
    private val ANIMATION_MS    = 6000L   // manage animation
    private val MAX_ROUND_MS    = 10 * 60_000L
    // ---------------------------------------------------------------------

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var btn: Button? = null
    private lateinit var wm: WindowManager

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val b = Button(this).apply {
            text = "START"
            setOnClickListener { toggle() }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; y = 400 }
        wm.addView(b, lp)
        btn = b
    }

    private fun toggle() {
        if (job?.isActive == true) {
            job?.cancel(); job = null; btn?.text = "START"
        } else {
            btn?.text = "STOP"
            job = scope.launch {
                try { while (isActive) cycle() } catch (e: CancellationException) { throw e }
                catch (e: Exception) { Log.e("IdleBot", "crashed", e) }
                finally { withContext(NonCancellable + Dispatchers.Main) { btn?.text = "START" } }
            }
        }
    }

    private suspend fun cycle() {
        tap(MANAGE_OR_START); delay(ANIMATION_MS)      // manage + animation
        tap(CONTINUE);        delay(2500)               // "You got" -> continue
        tap(MANAGE_OR_START); delay(3000)               // calendar START
        tap(AUTO_ASSIGN);     delay(1200)               // auto assign managers
        tap(LEVEL_START);     delay(4000)               // start match-3 (auto mode)
        waitForVictory()
        tap(CONTINUE);        delay(2500)               // victory -> continue
        tap(CONTINUE);        delay(2500)               // chapter completed -> continue
    }

    private suspend fun waitForVictory() {
        val end = System.currentTimeMillis() + MAX_ROUND_MS
        var hits = 0
        while (System.currentTimeMillis() < end) {
            delay(2000)  // takeScreenshot is rate limited to ~1/sec
            val bmp = shot() ?: continue
            hits = if (isVictory(bmp)) hits + 1 else 0
            bmp.recycle()
            if (hits >= 2) return
        }
    }

    // VICTORY panel is a uniform dark-blue band; check 4 points on its left/right edges.
    private fun isVictory(b: Bitmap): Boolean {
        val pts = listOf(0.08f to 0.53f, 0.92f to 0.53f, 0.08f to 0.64f, 0.92f to 0.64f)
        return pts.all { (fx, fy) ->
            val c = b.getPixel((fx * b.width).toInt(), (fy * b.height).toInt())
            val r = Color.red(c); val g = Color.green(c); val bl = Color.blue(c)
            bl > r + 25 && bl >= g && r + g + bl < 330
        }
    }

    private suspend fun tap(p: Pair<Float, Float>) {
        val bounds = wm.maximumWindowMetrics.bounds
        val path = Path().apply { moveTo(p.first * bounds.width(), p.second * bounds.height()) }
        suspendCancellableCoroutine<Unit> { c ->
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
            val ok = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { if (c.isActive) c.resume(Unit) }
                override fun onCancelled(d: GestureDescription?) { if (c.isActive) c.resume(Unit) }
            }, null)
            if (!ok && c.isActive) c.resume(Unit)
        }
    }

    private suspend fun shot(): Bitmap? = suspendCancellableCoroutine { c ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                val hb = r.hardwareBuffer
                val sw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                hb.close()
                if (c.isActive) c.resume(sw)
            }
            override fun onFailure(code: Int) { if (c.isActive) c.resume(null) }
        })
    }

    override fun onDestroy() {
        scope.cancel()
        btn?.let { wm.removeView(it) }
        super.onDestroy()
    }
}
