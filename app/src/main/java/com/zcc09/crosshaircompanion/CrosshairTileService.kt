package com.zcc09.crosshaircompanion

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class CrosshairTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (Store.masterEnabled(this@CrosshairTileService)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val target = !Store.masterEnabled(this)
        Store.setMasterEnabled(this, target)
        if (target && Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        } else if (!target) {
            OverlayService.stop(this)
        }
        qsTile?.apply {
            state = if (target) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
