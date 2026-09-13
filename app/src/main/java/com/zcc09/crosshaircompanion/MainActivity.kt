package com.zcc09.crosshaircompanion

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var swMaster: SwitchCompat
    private lateinit var swOnlySelected: SwitchCompat
    private lateinit var preview: CrosshairView
    private var refreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swMaster = findViewById(R.id.swMaster)
        swOnlySelected = findViewById(R.id.swOnlySelected)
        preview = findViewById(R.id.preview)

        swMaster.setOnCheckedChangeListener { _, checked ->
            if (refreshing) return@setOnCheckedChangeListener
            Store.setMasterEnabled(this, checked)
            if (checked) {
                if (!Settings.canDrawOverlays(this)) {
                    Store.setMasterEnabled(this, false)
                    swMaster.isChecked = false
                    toast("Grant \"Display over other apps\" first")
                    openOverlaySettings()
                    return@setOnCheckedChangeListener
                }
                maybeAskNotificationPermission()
                OverlayService.start(this)
            } else {
                OverlayService.stop(this)
            }
            refresh()
        }

        swOnlySelected.setOnCheckedChangeListener { _, checked ->
            if (refreshing) return@setOnCheckedChangeListener
            Store.setOnlySelected(this, checked)
            refresh()
        }

        findViewById<MaterialButton>(R.id.btnOverlayPerm).setOnClickListener { openOverlaySettings() }
        findViewById<MaterialButton>(R.id.btnUsagePerm).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) {
                toast("Cannot open usage access settings on this device")
            }
        }
        findViewById<MaterialButton>(R.id.btnNotifPerm).setOnClickListener { maybeAskNotificationPermission() }
        findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                toast("Not supported on this device")
            }
        }
        findViewById<MaterialButton>(R.id.btnEditActive).setOnClickListener {
            val active = Store.activeProfile(this)
            if (active == null) {
                toast("No styles defined yet")
            } else {
                startActivity(Intent(this, EditorActivity::class.java).putExtra("profileId", active.id))
            }
        }
        findViewById<View>(R.id.navGames).setOnClickListener {
            startActivity(Intent(this, GamesActivity::class.java))
        }
        findViewById<View>(R.id.navStyles).setOnClickListener {
            startActivity(Intent(this, StylesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (Store.masterEnabled(this) && Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
        refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        refreshing = true
        swMaster.isChecked = Store.masterEnabled(this)
        swOnlySelected.isChecked = Store.onlySelected(this)
        refreshing = false

        val active = Store.activeProfile(this)
        preview.spec = active?.spec ?: CrosshairSpec()
        findViewById<TextView>(R.id.tvActiveName).text = active?.name ?: "No styles"

        val overlayOk = Settings.canDrawOverlays(this)
        val usageOk = hasUsageAccess()
        val notifOk = if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val batteryOk = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)

        bindPermButton(R.id.btnOverlayPerm, overlayOk)
        bindPermButton(R.id.btnUsagePerm, usageOk)
        bindPermButton(R.id.btnNotifPerm, notifOk)
        bindPermButton(R.id.btnBattery, batteryOk)

        val rules = Store.loadRules(this)
        val enabledCount = rules.count { it.enabled }
        findViewById<TextView>(R.id.tvGamesSummary).text = "$enabledCount of ${rules.size} games enabled"
        val profiles = Store.loadProfiles(this)
        findViewById<TextView>(R.id.tvStylesSummary).text = "${profiles.size} styles"

        val master = Store.masterEnabled(this)
        val status = when {
            !overlayOk -> "Missing overlay permission"
            !master -> "Paused"
            !usageOk -> "Active everywhere — grant Usage access to limit to selected games"
            else -> "Active — crosshair shows over your selected games"
        }
        findViewById<TextView>(R.id.tvStatus).text = status
        findViewById<TextView>(R.id.tvVersion).text = "Version ${BuildConfig.VERSION_NAME}"
    }

    private fun bindPermButton(id: Int, granted: Boolean) {
        val b = findViewById<MaterialButton>(id)
        b.isEnabled = !granted
        b.text = if (granted) "Granted" else "Grant"
    }

    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            toast("Cannot open overlay settings")
        }
    }

    private fun hasUsageAccess(): Boolean = try {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    } catch (t: Throwable) {
        false
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}
