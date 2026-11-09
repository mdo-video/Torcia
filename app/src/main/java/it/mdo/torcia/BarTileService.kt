package it.mdo.torcia

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class BarTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update(OverlayService.running)
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            openApp()
            return
        }
        if (OverlayService.running) {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
            update(false)
        } else {
            try {
                startForegroundService(Intent(this, OverlayService::class.java))
                update(true)
            } catch (e: Exception) {
                openApp()
            }
        }
    }

    private fun update(active: Boolean) {
        val t = qsTile ?: return
        t.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Barra Luce"
        t.icon = Icon.createWithResource(this, R.drawable.ic_sun)
        t.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            startActivityAndCollapse(i)
        }
    }
}
