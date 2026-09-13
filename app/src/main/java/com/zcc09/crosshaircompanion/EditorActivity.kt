package com.zcc09.crosshaircompanion

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
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
import com.google.android.material.slider.Slider

class EditorActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var profile: Profile
    private lateinit var profiles: MutableList<Profile>
    private lateinit var spec: CrosshairSpec
    private lateinit var preview: CrosshairView
    private lateinit var previewBox: FrameLayout
    private lateinit var swOutline: SwitchCompat
    private lateinit var swActive: SwitchCompat

    private var previewDark = true
    private var syncing = false

    private val sliderBindings = mutableListOf<SliderBinding>()
    private val mainSwatches = mutableListOf<Pair<View, Int>>()
    private val outlineSwatches = mutableListOf<Pair<View, Int>>()

    private val palette = intArrayOf(
        Color.WHITE,
        0xFFFFEB3B.toInt(),
        0xFF39FF14.toInt(),
        0xFF00E5FF.toInt(),
        0xFFFF5252.toInt(),
        0xFFFF9800.toInt(),
        0xFFE040FB.toInt(),
        Color.BLACK
    )

    private class SliderBinding(
        val slider: Slider,
        val label: TextView,
        val title: String,
        val get: () -> Float
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileId = intent.getStringExtra("profileId")
        profiles = Store.loadProfiles(this)
        val found = profiles.firstOrNull { it.id == profileId }
        if (found == null) {
            toast("Style not found")
            finish()
            return
        }
        profile = found
        spec = profile.spec

        setContentView(R.layout.activity_editor)

        toolbar = findViewById(R.id.toolbar)
        toolbar.title = profile.name
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.editor_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.actionRename -> {
                    showRenameDialog()
                    true
                }
                R.id.actionDelete -> {
                    confirmDelete()
                    true
                }
                else -> false
            }
        }

        preview = findViewById(R.id.preview)
        previewBox = findViewById(R.id.previewBox)
        swOutline = findViewById(R.id.swOutline)
        swActive = findViewById(R.id.swActive)

        findViewById<MaterialButton>(R.id.btnPreviewDark).setOnClickListener {
            previewDark = true
            updatePreviewBg()
        }
        findViewById<MaterialButton>(R.id.btnPreviewLight).setOnClickListener {
            previewDark = false
            updatePreviewBg()
        }

        swActive.isChecked = Store.activeProfileId(this) == profile.id
        swActive.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                Store.setActiveProfileId(this, profile.id)
            } else if (Store.activeProfileId(this) == profile.id) {
                val other = profiles.firstOrNull { it.id != profile.id }
                if (other != null) {
                    Store.setActiveProfileId(this, other.id)
                } else {
                    swActive.isChecked = true
                }
            }
        }

        buildPresetButtons()
        buildShapeButtons()
        bindSliders()
        swOutline.isChecked = spec.outline
        swOutline.setOnCheckedChangeListener { _, checked ->
            if (syncing) return@setOnCheckedChangeListener
            spec.outline = checked
            preview.spec = spec
            save()
        }
        buildSwatches(R.id.colorRow, false)
        buildSwatches(R.id.outlineColorRow, true)
        updatePreviewBg()
        syncAll()
    }

    private fun buildPresetButtons() {
        val row = findViewById<LinearLayout>(R.id.presetsRow)
        row.removeAllViews()
        for (preset in CrosshairPresets.all()) {
            val b = layoutInflater.inflate(R.layout.chip_button, row, false) as MaterialButton
            b.text = preset.name
            b.setOnClickListener {
                spec = preset.spec.copy()
                syncAll()
                save()
            }
            row.addView(b)
        }
    }

    private fun buildShapeButtons() {
        val row = findViewById<LinearLayout>(R.id.shapeRow)
        row.removeAllViews()
        for (shape in CrosshairShape.values()) {
            val b = layoutInflater.inflate(R.layout.chip_button, row, false) as MaterialButton
            b.text = shapeDisplayName(shape)
            b.setOnClickListener {
                spec.shape = shape
                syncAll()
                save()
            }
            row.addView(b)
        }
    }

    private fun bindSliders() {
        bindSlider(findViewById(R.id.sliderSize), findViewById(R.id.lblSize), "Size", 4f, 64f, { spec.sizeDp }, { spec.sizeDp = it })
        bindSlider(findViewById(R.id.sliderThickness), findViewById(R.id.lblThickness), "Thickness", 0.5f, 10f, { spec.thicknessDp }, { spec.thicknessDp = it })
        bindSlider(findViewById(R.id.sliderGap), findViewById(R.id.lblGap), "Center gap", 0f, 30f, { spec.gapDp }, { spec.gapDp = it })
        bindSlider(findViewById(R.id.sliderDot), findViewById(R.id.lblDot), "Center dot", 0f, 16f, { spec.dotDp }, { spec.dotDp = it })
        bindSlider(findViewById(R.id.sliderOpacity), findViewById(R.id.lblOpacity), "Opacity", 10f, 100f, { spec.opacity.toFloat() }, { spec.opacity = it.toInt() })
        bindSlider(findViewById(R.id.sliderOffsetX), findViewById(R.id.lblOffsetX), "Offset X", -200f, 200f, { spec.offsetXDp }, { spec.offsetXDp = it })
        bindSlider(findViewById(R.id.sliderOffsetY), findViewById(R.id.lblOffsetY), "Offset Y", -200f, 200f, { spec.offsetYDp }, { spec.offsetYDp = it })
        bindSlider(findViewById(R.id.sliderOutlineWidth), findViewById(R.id.lblOutlineWidth), "Outline width", 0.5f, 3f, { spec.outlineWidthDp }, { spec.outlineWidthDp = it })
    }

    private fun bindSlider(
        slider: Slider,
        label: TextView,
        title: String,
        from: Float,
        to: Float,
        get: () -> Float,
        set: (Float) -> Unit
    ) {
        slider.valueFrom = from
        slider.valueTo = to
        slider.value = get().coerceIn(from, to)
        label.text = title + ": " + fmt(get())
        sliderBindings.add(SliderBinding(slider, label, title, get))
        slider.addOnChangeListener { _, value, _ ->
            if (syncing) return@addOnChangeListener
            set(value)
            label.text = title + ": " + fmt(value)
            preview.spec = spec
            save()
        }
    }

    private fun syncAll() {
        syncing = true
        for (b in sliderBindings) {
            b.slider.value = b.get().coerceIn(b.slider.valueFrom, b.slider.valueTo)
            b.label.text = b.title + ": " + fmt(b.get())
        }
        swOutline.isChecked = spec.outline
        refreshSwatches()
        preview.spec = spec
        syncing = false
    }

    private fun buildSwatches(rowId: Int, outline: Boolean) {
        val row = findViewById<LinearLayout>(rowId)
        row.removeAllViews()
        val list = if (outline) outlineSwatches else mainSwatches
        list.clear()
        val dp = resources.displayMetrics.density
        for (c in palette) {
            val v = View(this)
            val size = (40 * dp).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = (10 * dp).toInt()
            v.layoutParams = lp
            v.setOnClickListener {
                if (outline) spec.outlineColor = c else spec.color = c
                refreshSwatches()
                save()
            }
            row.addView(v)
            list.add(Pair(v, c))
        }
        refreshSwatches()
    }

    private fun refreshSwatches() {
        for (pair in mainSwatches) {
            pair.first.background = swatchDrawable(pair.second, spec.color == pair.second)
        }
        for (pair in outlineSwatches) {
            pair.first.background = swatchDrawable(pair.second, spec.outlineColor == pair.second)
        }
    }

    private fun swatchDrawable(color: Int, selected: Boolean): GradientDrawable {
        val dp = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(
                ((if (selected) 4 else 1) * dp).toInt(),
                if (selected) 0xFF4CAF50.toInt() else 0x55FFFFFF.toInt()
            )
        }
    }

    private fun updatePreviewBg() {
        val bg = GradientDrawable().apply {
            cornerRadius = 16f * resources.displayMetrics.density
            setColor(if (previewDark) 0xFF10151B.toInt() else 0xFFE8EAED.toInt())
        }
        previewBox.background = bg
    }

    private fun save() {
        profile.spec = spec
        Store.saveProfiles(this, profiles)
    }

    private fun showRenameDialog() {
        val input = EditText(this)
        input.setText(profile.name)
        input.setSelection(profile.name.length)
        val wrap = FrameLayout(this)
        val pad = (20 * resources.displayMetrics.density).toInt()
        wrap.setPadding(pad, pad / 2, pad, 0)
        wrap.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle("Rename style")
            .setView(wrap)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    profile.name = name
                    toolbar.title = name
                    save()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete() {
        if (profiles.size <= 1) {
            toast("At least one style is required")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete \"${profile.name}\"?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                profiles.removeAll { it.id == profile.id }
                if (Store.activeProfileId(this) == profile.id) {
                    Store.setActiveProfileId(this, profiles.first().id)
                }
                Store.saveProfiles(this, profiles)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fmt(v: Float): String {
        val r = Math.round(v * 10f) / 10f
        return if (r == Math.round(r).toFloat()) {
            r.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", r)
        }
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}
