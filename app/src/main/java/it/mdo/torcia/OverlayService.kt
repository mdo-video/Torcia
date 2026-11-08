package it.mdo.torcia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar

class OverlayService : Service() {

    companion object {
        @Volatile
        var running = false
        const val ACTION_STOP = "it.mdo.torcia.STOP"
        private const val CHANNEL = "torcia"
        private const val NOTIF_ID = 1
    }

    private class Snap(val x: Int, val w: Int, val b: Int, val h: Int)

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var root: FrameLayout? = null
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var barGroup: FrameLayout
    private lateinit var pill: FrameLayout
    private val handlePills = ArrayList<View>()

    private var on = true
    private var dim = 100
    private var xF = 0f
    private var wF = 1f
    private var bF = 0f
    private var hDp = 60f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("torcia", MODE_PRIVATE)
        on = prefs.getBoolean("on", true)
        dim = prefs.getInt("dim", 100)
        xF = prefs.getFloat("x", 0f)
        wF = prefs.getFloat("w", 1f)
        bF = prefs.getFloat("b", 0f)
        hDp = prefs.getFloat("h", 60f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) buildOverlay()
        running = true
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (root != null) refresh()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        root?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        root = null
        running = false
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Barra Luce", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_sun)
            .setContentTitle("Barra Luce attiva")
            .setContentText("Tocca per aprire")
            .setContentIntent(open)
            .addAction(0, "Spegni", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun realMetrics(): DisplayMetrics {
        val m = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(m)
        return m
    }

    private fun screenW(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width()
        else realMetrics().widthPixels

    private fun screenH(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.height()
        else realMetrics().heightPixels

    private fun buildOverlay() {
        val r = FrameLayout(this)

        barGroup = FrameLayout(this)
        r.addView(barGroup, FrameLayout.LayoutParams(-1, -1))

        val cap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(3f), dp(8f), dp(3f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A19"))
                cornerRadius = dp(17f).toFloat()
            }
        }

        val power = iconView(R.drawable.ic_power, 28, 4, "#F1EFE8", true)
        power.setOnClickListener {
            on = false
            refresh()
            save()
        }
        cap.addView(power)
        cap.addView(iconView(R.drawable.ic_sun_low, 14, 0, "#B4B2A9", false), gap(14))

        val seek = SeekBar(this).apply {
            max = 90
            progress = dim - 10
            progressTintList = ColorStateList.valueOf(Color.parseColor("#F1EFE8"))
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#F1EFE8"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    dim = p + 10
                    applyColors()
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}

                override fun onStopTrackingTouch(sb: SeekBar?) {
                    save()
                }
            })
        }
        cap.addView(seek, LinearLayout.LayoutParams(dp(76f), dp(28f)).apply { marginStart = dp(4f) })
        cap.addView(iconView(R.drawable.ic_sun, 14, 0, "#B4B2A9", false), gap(14))

        barGroup.addView(
            cap,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply { topMargin = dp(8f) }
        )

        val top = handle(80, 16, 44, 4, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        top.setOnTouchListener(dragger { _, dy, s ->
            val nh = (s.h + dy).coerceIn(dp(52f), maxOf(dp(52f), screenH() - s.b - topReserve()))
            hDp = nh / resources.displayMetrics.density
        })
        val left = handle(20, 56, 4, 28, Gravity.START or Gravity.CENTER_VERTICAL)
        left.setOnTouchListener(dragger { dx, _, s ->
            val sw = screenW()
            val right = s.x + s.w
            val nx = (s.x + dx).coerceIn(0, right - minOf(dp(190f), sw))
            xF = nx.toFloat() / sw
            wF = (right - nx).toFloat() / sw
        })
        val right = handle(20, 56, 4, 28, Gravity.END or Gravity.CENTER_VERTICAL)
        right.setOnTouchListener(dragger { dx, _, s ->
            val sw = screenW()
            val nw = (s.w + dx).coerceIn(minOf(dp(190f), sw), sw - s.x)
            wF = nw.toFloat() / sw
        })

        barGroup.setOnTouchListener(dragger { dx, dy, s ->
            xF = (s.x + dx).toFloat() / screenW()
            bF = (s.b + dy).toFloat() / screenH()
        })

        pill = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A19"))
                setStroke(dp(1f), Color.parseColor("#5F5E5A"))
                cornerRadius = dp(11f).toFloat()
            }
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_power)
                    setColorFilter(Color.parseColor("#B4B2A9"))
                },
                FrameLayout.LayoutParams(dp(14f), dp(14f), Gravity.CENTER)
            )
            setOnClickListener {
                on = true
                refresh()
                save()
            }
        }
        r.addView(pill, FrameLayout.LayoutParams(-1, -1))

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        applyColors()
        layoutNow()
        root = r
        wm.addView(r, lp)
    }

    private fun gap(wDp: Int) =
        LinearLayout.LayoutParams(dp(wDp.toFloat()), dp(28f)).apply { marginStart = dp(4f) }

    private fun iconView(res: Int, boxDp: Int, padDp: Int, color: String, clickable: Boolean): ImageView {
        val v: ImageView = if (clickable) ImageButton(this) else ImageView(this)
        v.setImageResource(res)
        v.setColorFilter(Color.parseColor(color))
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        v.setBackgroundColor(Color.TRANSPARENT)
        v.setPadding(dp(padDp.toFloat()), dp(padDp.toFloat()), dp(padDp.toFloat()), dp(padDp.toFloat()))
        v.layoutParams = LinearLayout.LayoutParams(dp(boxDp.toFloat()), dp(28f))
        return v
    }

    private fun handle(wDp: Int, hDpBox: Int, pw: Int, ph: Int, gravity: Int): FrameLayout {
        val f = FrameLayout(this)
        val p = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A19"))
                cornerRadius = dp(3f).toFloat()
            }
        }
        f.addView(p, FrameLayout.LayoutParams(dp(pw.toFloat()), dp(ph.toFloat()), Gravity.CENTER))
        handlePills.add(p)
        barGroup.addView(
            f,
            FrameLayout.LayoutParams(dp(wDp.toFloat()), dp(hDpBox.toFloat()), gravity)
        )
        return f
    }

    private fun applyColors() {
        val v = (dim * 2.55f).toInt().coerceIn(0, 255)
        barGroup.setBackgroundColor(Color.rgb(v, v, v))
        val hc = if (dim > 40) Color.parseColor("#1A1A19") else Color.parseColor("#888780")
        handlePills.forEach { (it.background as GradientDrawable).setColor(hc) }
    }

    private fun topReserve(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val status = if (id > 0) resources.getDimensionPixelSize(id) else dp(28f)
        return status + dp(12f)
    }

    private fun bottomReserve(): Int = dp(24f)

    private fun layoutNow() {
        val sw = screenW()
        val sh = screenH()
        val minW = minOf(dp(190f), sw)
        val w = (wF * sw).toInt().coerceIn(minW, sw)
        val x = (xF * sw).toInt().coerceIn(0, sw - w)
        val topRes = topReserve()
        val botRes = bottomReserve()
        val h = dp(hDp).coerceIn(minOf(dp(52f), sh), maxOf(dp(52f), sh - topRes - botRes))
        val pw = dp(44f)
        val ph = dp(22f)
        val curH = if (on) h else ph
        val b = (bF * sh).toInt().coerceIn(botRes, maxOf(botRes, sh - curH - topRes))

        wF = w.toFloat() / sw
        xF = x.toFloat() / sw
        bF = b.toFloat() / sh
        hDp = h / resources.displayMetrics.density

        lp.width = if (on) w else pw
        lp.height = curH
        lp.x = if (on) x else x + w / 2 - pw / 2
        lp.y = sh - b - curH
        barGroup.visibility = if (on) View.VISIBLE else View.GONE
        pill.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun refresh() {
        layoutNow()
        root?.let { if (it.isAttachedToWindow) wm.updateViewLayout(it, lp) }
    }

    private fun save() {
        prefs.edit()
            .putBoolean("on", on)
            .putInt("dim", dim)
            .putFloat("x", xF)
            .putFloat("w", wF)
            .putFloat("b", bF)
            .putFloat("h", hDp)
            .apply()
    }

    private fun snap(): Snap {
        val sw = screenW()
        val sh = screenH()
        return Snap((xF * sw).toInt(), (wF * sw).toInt(), (bF * sh).toInt(), dp(hDp))
    }

    private fun dragger(move: (Int, Int, Snap) -> Unit): View.OnTouchListener {
        var sx = 0f
        var sy = 0f
        var s = Snap(0, 0, 0, 0)
        return View.OnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                    s = snap()
                }
                MotionEvent.ACTION_MOVE -> {
                    move((e.rawX - sx).toInt(), (sy - e.rawY).toInt(), s)
                    refresh()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> save()
            }
            true
        }
    }
}
