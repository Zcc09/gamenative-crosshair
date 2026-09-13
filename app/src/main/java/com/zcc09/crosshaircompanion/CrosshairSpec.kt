package com.zcc09.crosshaircompanion

import android.graphics.Color
import org.json.JSONObject

enum class CrosshairShape { CROSS, CROSS_DOT, DOT, CIRCLE, CIRCLE_DOT, X, CHEVRON, T_SHAPE }

fun shapeDisplayName(s: CrosshairShape): String = when (s) {
    CrosshairShape.CROSS -> "Cross"
    CrosshairShape.CROSS_DOT -> "Cross + Dot"
    CrosshairShape.DOT -> "Dot"
    CrosshairShape.CIRCLE -> "Ring"
    CrosshairShape.CIRCLE_DOT -> "Ring + Dot"
    CrosshairShape.X -> "Diagonal X"
    CrosshairShape.CHEVRON -> "Chevron"
    CrosshairShape.T_SHAPE -> "T-Post"
}

data class CrosshairSpec(
    var shape: CrosshairShape = CrosshairShape.CROSS_DOT,
    var color: Int = Color.WHITE,
    var sizeDp: Float = 16f,
    var thicknessDp: Float = 2.5f,
    var gapDp: Float = 6f,
    var dotDp: Float = 2.5f,
    var opacity: Int = 100,
    var outline: Boolean = true,
    var outlineColor: Int = Color.BLACK,
    var outlineWidthDp: Float = 1f,
    var offsetXDp: Float = 0f,
    var offsetYDp: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("shape", shape.name)
        put("color", color)
        put("size", sizeDp.toDouble())
        put("thickness", thicknessDp.toDouble())
        put("gap", gapDp.toDouble())
        put("dot", dotDp.toDouble())
        put("opacity", opacity)
        put("outline", outline)
        put("outlineColor", outlineColor)
        put("outlineWidth", outlineWidthDp.toDouble())
        put("offsetX", offsetXDp.toDouble())
        put("offsetY", offsetYDp.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): CrosshairSpec {
            val shape = try {
                CrosshairShape.valueOf(o.optString("shape", "CROSS_DOT"))
            } catch (e: Exception) {
                CrosshairShape.CROSS_DOT
            }
            return CrosshairSpec(
                shape = shape,
                color = o.optInt("color", Color.WHITE),
                sizeDp = o.optDouble("size", 16.0).toFloat(),
                thicknessDp = o.optDouble("thickness", 2.5).toFloat(),
                gapDp = o.optDouble("gap", 6.0).toFloat(),
                dotDp = o.optDouble("dot", 2.5).toFloat(),
                opacity = o.optInt("opacity", 100),
                outline = o.optBoolean("outline", true),
                outlineColor = o.optInt("outlineColor", Color.BLACK),
                outlineWidthDp = o.optDouble("outlineWidth", 1.0).toFloat(),
                offsetXDp = o.optDouble("offsetX", 0.0).toFloat(),
                offsetYDp = o.optDouble("offsetY", 0.0).toFloat()
            )
        }
    }
}

class CrosshairPreset(val name: String, val spec: CrosshairSpec)

object CrosshairPresets {

    private fun rgb(v: Int): Int = Color.rgb((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)

    fun all(): List<CrosshairPreset> = listOf(
        CrosshairPreset("Classic Cross", CrosshairSpec(CrosshairShape.CROSS, Color.WHITE, 16f, 2.5f, 6f, 2.5f)),
        CrosshairPreset("Cross + Dot", CrosshairSpec(CrosshairShape.CROSS_DOT, Color.WHITE, 16f, 2.5f, 7f, 2.5f)),
        CrosshairPreset("Center Dot", CrosshairSpec(CrosshairShape.DOT, rgb(0xFF5252), 4f, 2.5f, 0f, 4f)),
        CrosshairPreset("Tactical Green", CrosshairSpec(CrosshairShape.CROSS_DOT, rgb(0x39FF14), 15f, 2.5f, 6f, 2f)),
        CrosshairPreset("Ring", CrosshairSpec(CrosshairShape.CIRCLE, Color.WHITE, 14f, 2.5f, 0f, 2.5f)),
        CrosshairPreset("Ring + Dot", CrosshairSpec(CrosshairShape.CIRCLE_DOT, Color.WHITE, 14f, 2.5f, 0f, 2.5f)),
        CrosshairPreset("Diagonal X", CrosshairSpec(CrosshairShape.X, Color.WHITE, 14f, 2.5f, 4f, 2.5f)),
        CrosshairPreset("Chevron", CrosshairSpec(CrosshairShape.CHEVRON, rgb(0x00E5FF), 12f, 3f, 0f, 2.5f)),
        CrosshairPreset("T-Post", CrosshairSpec(CrosshairShape.T_SHAPE, Color.WHITE, 16f, 2.5f, 6f, 2.5f)),
        CrosshairPreset("Gold Dot", CrosshairSpec(CrosshairShape.DOT, rgb(0xFFD54F), 5f, 2.5f, 0f, 5f))
    )
}
