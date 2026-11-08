package it.mdo.torcia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var perm: Button
    private lateinit var toggle: Button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
        }

        col.addView(TextView(this).apply {
            text = "Torcia"
            textSize = 30f
            gravity = Gravity.CENTER
        })
        col.addView(TextView(this).apply {
            text = "Una barra luminosa sopra le altre app. Regola altezza, larghezza e posizione con le maniglie; acceso/spento e dimmer sono dentro la barra."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        })
        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad)
        }
        col.addView(status)

        perm = Button(this).apply {
            text = "Concedi il permesso sopra le altre app"
            setOnClickListener { openPermissionScreen() }
        }
        col.addView(perm)

        toggle = Button(this).apply {
            setOnClickListener { onToggle() }
        }
        col.addView(toggle)

        setContentView(col)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun openPermissionScreen() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun onToggle() {
        if (!Settings.canDrawOverlays(this)) {
            openPermissionScreen()
            return
        }
        if (OverlayService.running) {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        } else {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            startForegroundService(Intent(this, OverlayService::class.java))
            handler.postDelayed({ moveTaskToBack(true) }, 300)
        }
        handler.postDelayed({ refresh() }, 400)
    }

    private fun refresh() {
        val granted = Settings.canDrawOverlays(this)
        val running = OverlayService.running
        perm.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
        toggle.text = if (running) "Spegni la torcia" else "Accendi la torcia"
        status.text = when {
            !granted -> "Serve il permesso per mostrare la barra sopra le altre app."
            running -> "La barra è attiva."
            else -> "La barra è spenta."
        }
    }
}
