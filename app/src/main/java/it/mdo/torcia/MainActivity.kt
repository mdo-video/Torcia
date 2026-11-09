package it.mdo.torcia

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

class MainActivity : AppCompatActivity() {

    private lateinit var status: MaterialTextView
    private lateinit var perm: MaterialButton
    private lateinit var toggle: MaterialButton
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (24 * d).toInt()
        val gap = (12 * d).toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
        }

        col.addView(MaterialTextView(this).apply {
            text = "Barra Luce"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineLarge)
            gravity = Gravity.CENTER
        })
        col.addView(MaterialTextView(this).apply {
            text = "Una barra luminosa sopra le altre app. Regola altezza, larghezza e posizione con le maniglie; acceso/spento e dimmer sono dentro la barra."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            gravity = Gravity.CENTER
            setPadding(0, gap, 0, pad)
        })
        status = MaterialTextView(this).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad)
        }
        col.addView(status)

        perm = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Concedi il permesso sopra le altre app"
            setOnClickListener { openPermissionScreen() }
        }
        col.addView(perm, buttonParams(gap))

        val tile = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Aggiungi ai toggle rapidi"
            setOnClickListener { addTile() }
        }
        col.addView(tile, buttonParams(gap))

        toggle = MaterialButton(this).apply {
            setOnClickListener { onToggle() }
        }
        col.addView(toggle, buttonParams(gap))

        setContentView(col)
    }

    private fun buttonParams(gap: Int) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = gap }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(this, BarTileService::class.java),
                    "Barra Luce",
                    Icon.createWithResource(this, R.drawable.ic_sun),
                    mainExecutor,
                    java.util.function.Consumer<Int> { }
                )
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(
            this,
            "Apri i toggle rapidi, tocca la matita e trascina Barra Luce tra i tasti attivi.",
            Toast.LENGTH_LONG
        ).show()
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
        perm.visibility = if (granted) View.GONE else View.VISIBLE
        toggle.text = if (running) "Spegni la barra" else "Accendi la barra"
        status.text = when {
            !granted -> "Serve il permesso per mostrare la barra sopra le altre app."
            running -> "La barra è attiva."
            else -> "La barra è spenta."
        }
    }
}
