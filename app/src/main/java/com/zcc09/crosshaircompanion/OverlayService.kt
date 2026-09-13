package com.zcc09.crosshaircompanion

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that draws the crosshair overlay.
 *
 * Visibility logic: the crosshair is shown only while one of the enabled
 * "game" rules (apps) is in the foreground, detected through the Usage Access
 * API. If Usage Access has not been granted, the overlay falls back to
 * showing over every app while the master switch is on.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_TOGGLE = "com.zcc09.crosshaircompanion.action.TOGGLE"
        const val ACTION_STOP = "com.zcc09.crosshaircompanion.action.STOP"
        const val ACTION_NEXT_STYLE = "com.zcc09.crosshaircompanion.action.NEXT_STYLE"

        /** GameNative (or any app) can report the currently running Windows game:
         *  adb/intent action with a string extra "game". Enables per-game styles. */
        const val ACTION_GAME_CHANGED = "com.zcc09.crosshaircompanion.action.GAME_CHANGED"
        const val EXTRA_GAME = "game"

        const val CHANNEL_ID = "crosshair_overlay"
        const val NOTIF_ID = 42

        fun start(ctx: Context) {
            val i = Intent(ctx, OverlayService::class.java)
            try {
                ctx.startForegroundService(i)
            } catch (e: Exception) {
                try {
                    ctx.startService(i)
                } catch (e2: Exception) {
                }
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, OverlayService::class.java))
            } catch (e: Exception) {
            }
        }
    }

    private var wm: WindowManager? = null
    private var view: CrosshairView? = null
    private var screenOn = true
    private var lastFg: String? = null
    private var currentGame: String? = null
    private var lastNotifKey = ""
    private var lastSpecKey = ""
    private val handler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> screenOn = false
                Intent.ACTION_SCREEN_ON -> screenOn = true
                ACTION_GAME_CHANGED -> currentGame = intent.getStringExtra(EXTRA_GAME)
            }
            update()
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            update()
            val delay = if (Store.masterEnabled(this@OverlayService)) 800L else 2000L
            handler.postDelayed(this, delay)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannel()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(ACTION_GAME_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        screenOn = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> Store.setMasterEnabled(this, !Store.masterEnabled(this))
            ACTION_NEXT_STYLE -> cycleActiveProfile()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForegroundCompat()
        update()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(poll, 800L)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        view?.let { v ->
            try {
                wm?.removeView(v)
            } catch (e: Exception) {
            }
        }
        view = null
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, "Crosshair overlay", NotificationManager.IMPORTANCE_LOW)
        ch.description = "Crosshair Companion status"
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun scopeText(): String {
        val rules = Store.loadRules(this)
        val watched = rules.count { it.enabled && it.kind == RuleKind.APP }
        return if (Store.onlySelected(this)) "$watched game app(s) watched" else "all apps"
    }

    private fun buildNotification(): Notification {
        val master = Store.masterEnabled(this)
        val active = Store.activeProfile(this)
        val text = (active?.name ?: "No style") + " • " + scopeText()
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_crosshair)
            .setContentTitle(if (master) "Crosshair overlay active" else "Crosshair overlay paused")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, if (master) "Pause" else "Resume", servicePendingIntent(ACTION_TOGGLE, 1))
            .addAction(0, "Next style", servicePendingIntent(ACTION_NEXT_STYLE, 2))
            .addAction(0, "Stop", servicePendingIntent(ACTION_STOP, 3))
            .build()
    }

    private fun servicePendingIntent(action: String, code: Int): PendingIntent {
        val i = Intent(this, OverlayService::class.java).setAction(action)
        return PendingIntent.getService(
            this, code, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun update() {
        val master = Store.masterEnabled(this)
        val onlySelected = Store.onlySelected(this)
        val profiles = Store.loadProfiles(this)
        val rules = Store.loadRules(this)
        val active = profiles.firstOrNull { it.id == Store.activeProfileId(this) } ?: profiles.firstOrNull()

        var spec: CrosshairSpec = active?.spec ?: CrosshairSpec()
        var show = false

        if (master && screenOn) {
            if (!onlySelected || !hasUsageAccess()) {
                show = true
            } else {
                val fgNow = foregroundPackage()
                if (fgNow != null) lastFg = fgNow
                val fg = lastFg
                if (fg != null && fg != packageName) {
                    val rule = rules.firstOrNull { it.kind == RuleKind.APP && it.enabled && it.pkg == fg }
                    if (rule != null) {
                        show = true
                        val rid = rule.profileId
                        if (rid != null) {
                            profiles.firstOrNull { it.id == rid }?.let { spec = it.spec }
                        }
                    }
                }
                val game = currentGame
                if (game != null) {
                    val gr = rules.firstOrNull {
                        it.kind == RuleKind.GAMENATIVE_GAME && it.enabled && it.label.equals(game, true)
                    }
                    val gid = gr?.profileId
                    if (gid != null) {
                        profiles.firstOrNull { it.id == gid }?.let { spec = it.spec }
                    }
                }
            }
        }

        applyView(show, spec)
        updateNotification()
    }

    private fun applyView(show: Boolean, spec: CrosshairSpec) {
        if (show && Settings.canDrawOverlays(this)) {
            var v = view
            if (v == null) {
                val nv = CrosshairView(this)
                nv.spec = spec
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.TOP or Gravity.START
                try {
                    wm?.addView(nv, params)
                } catch (e: Exception) {
                    return
                }
                view = nv
                v = nv
                lastSpecKey = ""
            }
            val key = spec.toJson().toString()
            if (key != lastSpecKey) {
                v.spec = spec
                lastSpecKey = key
            }
        } else {
            val v = view
            if (v != null) {
                try {
                    wm?.removeView(v)
                } catch (e: Exception) {
                }
            }
            view = null
            lastSpecKey = ""
        }
    }

    private fun updateNotification() {
        val master = Store.masterEnabled(this)
        val active = Store.activeProfile(this)
        val key = (if (master) "1" else "0") + "|" + (active?.name ?: "") + "|" + scopeText()
        if (key != lastNotifKey) {
            lastNotifKey = key
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification())
            } catch (e: Exception) {
            }
        }
    }

    private fun foregroundPackage(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val windowMs = if (lastFg == null) 6 * 60 * 60 * 1000L else 10 * 1000L
            val events = usm.queryEvents(now - windowMs, now) ?: return null
            val e = UsageEvents.Event()
            var lastPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val t = e.eventType
                if (t == UsageEvents.Event.ACTIVITY_RESUMED || t == UsageEvents.Event.ACTIVITY_PAUSED) {
                    lastPkg = e.packageName
                }
            }
            lastPkg
        } catch (t: Throwable) {
            null
        }
    }

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (t: Throwable) {
            false
        }
    }

    private fun cycleActiveProfile() {
        val list = Store.loadProfiles(this)
        if (list.size < 2) return
        val cur = Store.activeProfileId(this)
        val idx = list.indexOfFirst { it.id == cur }
        val next = list[(idx + 1) % list.size]
        Store.setActiveProfileId(this, next.id)
    }
}
