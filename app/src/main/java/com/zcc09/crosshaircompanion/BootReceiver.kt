package com.zcc09.crosshaircompanion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Store.masterEnabled(context) && Settings.canDrawOverlays(context)) {
            try {
                OverlayService.start(context)
            } catch (e: Exception) {
            }
        }
    }
}
