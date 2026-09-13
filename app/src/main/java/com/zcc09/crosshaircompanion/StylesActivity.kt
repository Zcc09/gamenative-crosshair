package com.zcc09.crosshaircompanion

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class StylesActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_styles)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { finish() }

        listContainer = findViewById(R.id.listContainer)
        findViewById<MaterialButton>(R.id.btnNewStyle).setOnClickListener { showNewStyleDialog() }
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    private fun describe(spec: CrosshairSpec): String {
        val colorName = when (spec.color) {
            0xFFFFFFFF.toInt() -> "white"
            0xFFFF5252.toInt() -> "red"
            0xFF39FF14.toInt() -> "green"
            0xFF00E5FF.toInt() -> "cyan"
            0xFFFFD54F.toInt() -> "gold"
            else -> "custom"
        }
        return "${shapeDisplayName(spec.shape)} · $colorName · ${spec.sizeDp.toInt()} dp"
    }

    private fun rebuild() {
        listContainer.removeAllViews()
        val profiles = Store.loadProfiles(this)
        val activeId = Store.activeProfileId(this)
        if (profiles.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No styles defined yet."
            tv.setPadding(0, 32, 0, 32)
            listContainer.addView(tv)
            return
        }
        for (p in profiles) {
            val row = layoutInflater.inflate(R.layout.item_style, listContainer, false)
            row.findViewById<CrosshairView>(R.id.previewMini).spec = p.spec
            row.findViewById<TextView>(R.id.tvName).text = p.name
            row.findViewById<TextView>(R.id.tvSub).text = describe(p.spec)
            val isActive = p.id == activeId
            val btn = row.findViewById<MaterialButton>(R.id.btnUse)
            btn.text = if (isActive) "Active" else "Use"
            btn.isEnabled = !isActive
            btn.setOnClickListener {
                Store.setActiveProfileId(this, p.id)
                rebuild()
            }
            row.setOnClickListener {
                startActivity(
                    Intent(this, EditorActivity::class.java).putExtra("profileId", p.id)
                )
            }
            row.setOnLongClickListener {
                confirmDelete(p)
                true
            }
            listContainer.addView(row)
        }
    }

    private fun confirmDelete(p: Profile) {
        val profiles = Store.loadProfiles(this)
        if (profiles.size <= 1) {
            toast("At least one style is required")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete \"${p.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                profiles.removeAll { it.id == p.id }
                if (Store.activeProfileId(this) == p.id) {
                    Store.setActiveProfileId(this, profiles.first().id)
                }
                Store.saveProfiles(this, profiles)
                rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewStyleDialog() {
        val input = EditText(this)
        val profiles = Store.loadProfiles(this)
        input.setText("My style ${profiles.size + 1}")
        val wrap = FrameLayout(this)
        val pad = (20 * resources.displayMetrics.density).toInt()
        wrap.setPadding(pad, pad / 2, pad, 0)
        wrap.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle("New style")
            .setView(wrap)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "My style ${profiles.size + 1}" }
                val base = Store.activeProfile(this)?.spec?.copy() ?: CrosshairSpec()
                val p = Profile.new(name, base)
                profiles.add(p)
                Store.saveProfiles(this, profiles)
                startActivity(
                    Intent(this, EditorActivity::class.java).putExtra("profileId", p.id)
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}
