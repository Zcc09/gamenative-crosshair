package com.zcc09.crosshaircompanion

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class GamesActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_games)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { finish() }

        listContainer = findViewById(R.id.listContainer)
        findViewById<MaterialButton>(R.id.btnAddApp).setOnClickListener { showAddAppDialog() }
        findViewById<MaterialButton>(R.id.btnAddGame).setOnClickListener { showAddGameDialog() }
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    private fun rebuild() {
        listContainer.removeAllViews()
        val rules = Store.loadRules(this)
        if (rules.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No games added yet — use the buttons below."
            tv.setPadding(0, 32, 0, 32)
            listContainer.addView(tv)
            return
        }
        val profiles = Store.loadProfiles(this)
        for (rule in rules) {
            val row = layoutInflater.inflate(R.layout.item_game, listContainer, false)
            row.findViewById<TextView>(R.id.tvName).text = rule.label
            row.findViewById<TextView>(R.id.tvSub).text = when (rule.kind) {
                RuleKind.APP -> rule.pkg ?: ""
                RuleKind.GAMENATIVE_GAME -> "GameNative game — auto-switches via integration"
            }
            val sw = row.findViewById<SwitchCompat>(R.id.swEnabled)
            sw.isChecked = rule.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                rule.enabled = checked
                Store.saveRules(this, rules)
            }
            val styleName = rule.profileId?.let { pid ->
                profiles.firstOrNull { it.id == pid }?.name
            } ?: "Default"
            val btnStyle = row.findViewById<MaterialButton>(R.id.btnStyle)
            btnStyle.text = "Style: $styleName"
            btnStyle.setOnClickListener { showStylePicker(rule, rules) }
            row.setOnLongClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Remove \"${rule.label}\"?")
                    .setPositiveButton("Remove") { _, _ ->
                        rules.remove(rule)
                        Store.saveRules(this, rules)
                        rebuild()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            listContainer.addView(row)
        }
    }

    private fun showStylePicker(rule: Rule, rules: MutableList<Rule>) {
        val profiles = Store.loadProfiles(this)
        val names = ArrayList<String>()
        names.add("Default (active style)")
        profiles.forEach { names.add(it.name) }
        var preselect = 0
        if (rule.profileId != null) {
            val i = profiles.indexOfFirst { it.id == rule.profileId }
            if (i >= 0) preselect = i + 1
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Crosshair for ${rule.label}")
            .setSingleChoiceItems(names.toTypedArray(), preselect) { dialog, which ->
                rule.profileId = if (which == 0) null else profiles[which - 1].id
                Store.saveRules(this, rules)
                dialog.dismiss()
                rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddAppDialog() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        val seen = HashSet<String>()
        val labels = ArrayList<String>()
        val pkgs = ArrayList<String>()
        for (ri in resolved) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName) continue
            if (!seen.add(pkg)) continue
            val label = try {
                ri.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }
            labels.add(label)
            pkgs.add(pkg)
        }
        // sort both lists by label
        val order = labels.indices.sortedBy { labels[it].lowercase() }
        val sortedLabels = order.map { labels[it] }
        val sortedPkgs = order.map { pkgs[it] }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add a game or app")
            .setItems(sortedLabels.toTypedArray()) { _, which ->
                val pkg = sortedPkgs[which]
                val rules = Store.loadRules(this)
                if (rules.any { it.pkg == pkg && it.kind == RuleKind.APP }) {
                    toast("Already in the list")
                } else {
                    rules.add(Rule.new(sortedLabels[which], pkg, RuleKind.APP))
                    Store.saveRules(this, rules)
                    rebuild()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddGameDialog() {
        val input = EditText(this)
        input.hint = "e.g. Metal Gear Solid: Peace Walker"
        val wrap = FrameLayout(this)
        val pad = (20 * resources.displayMetrics.density).toInt()
        wrap.setPadding(pad, pad / 2, pad, 0)
        wrap.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle("Add GameNative game")
            .setMessage(
                "Per-game styles activate automatically once GameNative reports the running game " +
                    "(see README for the one-line integration). Until then you can set the style " +
                    "as active and switch with the notification's \"Next style\" button."
            )
            .setView(wrap)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val rules = Store.loadRules(this)
                    rules.add(Rule.new(name, null, RuleKind.GAMENATIVE_GAME))
                    Store.saveRules(this, rules)
                    rebuild()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}
