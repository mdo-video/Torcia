package it.mdo.torcia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        @Volatile
        var running = false
        const val ACTION_STOP = "it.mdo.torcia.STOP"
        const val ACTION_TOGGLE = "it.mdo.torcia.TOGGLE"
        private const val CHANNEL = "barraluce"
        private const val NOTIF_ID = 1
    }

    private class Snap(val x: Int, val w: Int, val b: Int, val h: Int)

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var ui: Context

    private var root: FrameLayout? = null
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var barGroup: FrameLayout
    private lateinit var pill: FrameLayout
    private val handlePills = ArrayList<View>()

    private var on = true
    private var dim = 100
    private var op = 100
    private var xF = 0f
    private var wF = 1f
    private var bF = 0f
    private var hDp = 72f
    private var pF = 0.5f
    private var qF = 0.1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("barraluce", MODE_PRIVATE)
        ui = DynamicColors.wrapContextIfAvailable(ContextThemeWrapper(this, R.style.Theme_BarraLuce))
        on = prefs.getBoolean("on", true)
        dim = prefs.getInt("dim", 100).coerceIn(10, 100)
        op = prefs.getInt("op", 100).coerceIn(10, 100)
        xF = prefs.getFloat("x", 0f)
        wF = prefs.getFloat("w", 1f)
        bF = prefs.getFloat("b", 0f)
        hDp = prefs.getFloat("h", 72f)
        pF = prefs.getFloat("p", 0.5f)
        qF = prefs.getFloat("q", 0.1f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                if (root != null) setBarOn(!on)
                return START_STICKY
            }
        }
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) buildOverlay()
        running = true
        TileService.requestListeningState(this, ComponentName(this, BarTileService::class.java))
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (root != null) refresh()
    }

    override fun onDestroy() {
        root?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        root = null
        running = false
        TileService.requestListeningState(this, ComponentName(this, BarTileService::class.java))
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Barra Luce", NotificationManager.IMPORTANCE_LOW)
        )
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val rv = RemoteViews(packageName, R.layout.notif_collapsed)
        rv.setTextViewText(R.id.n_text, if (on) "Barra accesa" else "Barra spenta")
        rv.setTextViewText(R.id.n_toggle, if (on) "Spegni" else "Accendi")
        rv.setTextViewText(R.id.n_close, "Chiudi")
        rv.setOnClickPendingIntent(R.id.n_toggle, toggle)
        rv.setOnClickPendingIntent(R.id.n_close, stop)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_sun)
            .setContentTitle("Barra Luce")
            .setContentText(if (on) "Barra accesa" else "Barra spenta")
            .setContentIntent(open)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(rv)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun setBarOn(v: Boolean) {
        on = v
        refresh()
        save()
        updateNotification()
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
        val r = FrameLayout(ui)
        val subtle = MaterialColors.getColor(ui, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)

        barGroup = FrameLayout(ui)
        r.addView(barGroup, FrameLayout.LayoutParams(-1, -1))

        val cap = MaterialCardView(ui, null, com.google.android.material.R.attr.materialCardViewFilledStyle)
        cap.radius = dp(22f).toFloat()
        val row = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(4f), dp(12f), dp(4f))
        }
        cap.addView(row, FrameLayout.LayoutParams(-2, -2))

        val power = MaterialButton(ui, null, com.google.android.material.R.attr.materialIconButtonFilledTonalStyle)
        power.setIconResource(R.drawable.ic_power)
        power.iconSize = dp(20f)
        power.iconPadding = 0
        power.insetTop = 0
        power.insetBottom = 0
        power.minWidth = 0
        power.minHeight = 0
        power.minimumWidth = 0
        power.minimumHeight = 0
        power.setPadding(0, 0, 0, 0)
        power.setOnClickListener { setBarOn(false) }
        row.addView(power, LinearLayout.LayoutParams(dp(36f), dp(36f)))

        row.addView(sunIcon(R.drawable.ic_sun_low, subtle), iconParams(8f))
        row.addView(
            makeSlider(dim.toFloat()) {
                dim = it
                applyColors()
            },
            LinearLayout.LayoutParams(dp(120f), dp(36f))
        )
        row.addView(sunIcon(R.drawable.ic_sun, subtle), iconParams(0f))
        row.addView(sunIcon(R.drawable.ic_opacity, subtle), iconParams(12f))
        row.addView(
            makeSlider(op.toFloat()) {
                op = it
                applyColors()
            },
            LinearLayout.LayoutParams(dp(120f), dp(36f))
        )

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
            val nh = (s.h + dy).coerceIn(dp(64f), maxOf(dp(64f), screenH() - s.b - topReserve()))
            hDp = nh / resources.displayMetrics.density
        })
        val left = handle(20, 56, 4, 28, Gravity.START or Gravity.CENTER_VERTICAL)
        left.setOnTouchListener(dragger { dx, _, s ->
            val sw = screenW()
            val rightEdge = s.x + s.w
            val nx = (s.x + dx).coerceIn(0, rightEdge - minOf(dp(420f), sw))
            xF = nx.toFloat() / sw
            wF = (rightEdge - nx).toFloat() / sw
        })
        val right = handle(20, 56, 4, 28, Gravity.END or Gravity.CENTER_VERTICAL)
        right.setOnTouchListener(dragger { dx, _, s ->
            val sw = screenW()
            val nw = (s.w + dx).coerceIn(minOf(dp(420f), sw), sw - s.x)
            wF = nw.toFloat() / sw
        })

        barGroup.setOnTouchListener(dragger { dx, dy, s ->
            xF = (s.x + dx).toFloat() / screenW()
            bF = (s.b + dy).toFloat() / screenH()
        })

        val pillCard = MaterialCardView(ui, null, com.google.android.material.R.attr.materialCardViewFilledStyle)
        pillCard.radius = dp(18f).toFloat()
        pillCard.addView(
            ImageView(ui).apply {
                setImageResource(R.drawable.ic_power)
                setColorFilter(subtle)
            },
            FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER)
        )
        pillCard.setOnTouchListener(pillTouch())
        pill = pillCard
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

    private fun iconParams(startDp: Float) =
        LinearLayout.LayoutParams(dp(18f), dp(28f)).apply { marginStart = dp(startDp) }

    private fun makeSlider(start: Float, onValue: (Int) -> Unit): Slider {
        val sl = Slider(ui)
        sl.valueFrom = 10f
        sl.valueTo = 100f
        sl.stepSize = 1f
        sl.value = start
        sl.labelBehavior = LabelFormatter.LABEL_GONE
        sl.isTickVisible = false
        sl.trackHeight = dp(10f)
        sl.addOnChangeListener(Slider.OnChangeListener { _, v, _ -> onValue(v.toInt()) })
        sl.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(x: Slider) {}
            override fun onStopTrackingTouch(x: Slider) {
                save()
            }
        })
        return sl
    }

    private fun sunIcon(res: Int, color: Int): ImageView {
        val v = ImageView(ui)
        v.setImageResource(res)
        v.setColorFilter(color)
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        return v
    }

    private fun handle(wDp: Int, hDpBox: Int, pw: Int, ph: Int, gravity: Int): FrameLayout {
        val f = FrameLayout(ui)
        val p = View(ui).apply {
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
        val a = (op * 2.55f).toInt().coerceIn(0, 255)
        barGroup.setBackgroundColor(Color.argb(a, v, v, v))
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
        val minW = minOf(dp(420f), sw)
        val w = (wF * sw).toInt().coerceIn(minW, sw)
        val x = (xF * sw).toInt().coerceIn(0, sw - w)
        val topRes = topReserve()
        val botRes = bottomReserve()
        val h = dp(hDp).coerceIn(minOf(dp(64f), sh), maxOf(dp(64f), sh - topRes - botRes))
        val pw = dp(36f)
        val ph = dp(36f)
        val curH = if (on) h else ph
        val b = (bF * sh).toInt().coerceIn(botRes, maxOf(botRes, sh - h - topRes))
        val q = (qF * sh).toInt().coerceIn(botRes, maxOf(botRes, sh - ph - topRes))
        val px = ((pF * sw).toInt() - pw / 2).coerceIn(0, sw - pw)

        wF = w.toFloat() / sw
        xF = x.toFloat() / sw
        bF = b.toFloat() / sh
        qF = q.toFloat() / sh
        pF = (px + pw / 2).toFloat() / sw
        hDp = h / resources.displayMetrics.density

        lp.width = if (on) w else pw
        lp.height = curH
        lp.x = if (on) x else px
        lp.y = sh - (if (on) b else q) - curH
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
            .putInt("op", op)
            .putFloat("x", xF)
            .putFloat("w", wF)
            .putFloat("b", bF)
            .putFloat("h", hDp)
            .putFloat("p", pF)
            .putFloat("q", qF)
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

    private fun pillTouch(): View.OnTouchListener {
        var sx = 0f
        var sy = 0f
        var p0 = 0f
        var b0 = 0f
        var moved = false
        return View.OnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                    p0 = pF
                    b0 = qF
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx
                    val dy = sy - e.rawY
                    if (!moved && (abs(dx) > dp(8f) || abs(dy) > dp(8f))) moved = true
                    if (moved) {
                        pF = p0 + dx / screenW()
                        qF = b0 + dy / screenH()
                        refresh()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) setBarOn(true) else save()
                }
                MotionEvent.ACTION_CANCEL -> save()
            }
            true
        }
    }
}
