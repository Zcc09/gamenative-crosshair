package com.zcc09.crosshaircompanion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.View

/**
 * Draws a crosshair centered on the view (plus optional offsets).
 * Shared by the in-app previews and the system overlay window.
 */
class CrosshairView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var spec: CrosshairSpec = CrosshairSpec()
        set(value) {
            field = value
            invalidate()
        }

    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density: Float = resources.displayMetrics.density
    private var drawLogs = 0

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (BuildConfig.DEBUG && drawLogs < 20) {
            drawLogs++
            Log.i("CrosshairView", "CrosshairView draw #$drawLogs w=$width h=$height shape=${spec.shape} color=${spec.color}")
        }
        val s = spec
        val cx = width / 2f + s.offsetXDp * density
        val cy = height / 2f + s.offsetYDp * density
        val alpha = (s.opacity.coerceIn(0, 100) * 255) / 100

        val size = s.sizeDp * density
        val thick = s.thicknessDp * density
        val gap = s.gapDp * density
        val dot = s.dotDp * density
        val outlineW = if (s.outline) s.outlineWidthDp * density else 0f

        if (outlineW > 0f) {
            drawPass(canvas, outlinePaint, s.outlineColor, alpha, outlineW, s, cx, cy, size, thick, gap, dot)
        }
        drawPass(canvas, mainPaint, s.color, alpha, 0f, s, cx, cy, size, thick, gap, dot)
    }

    private fun drawPass(
        canvas: Canvas,
        paint: Paint,
        color: Int,
        alpha: Int,
        expand: Float,
        s: CrosshairSpec,
        cx: Float,
        cy: Float,
        size: Float,
        thick: Float,
        gap: Float,
        dot: Float
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        paint.alpha = alpha
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = thick + 2f * expand
        paint.strokeCap = if (expand > 0f) Paint.Cap.ROUND else Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.ROUND

        when (s.shape) {
            CrosshairShape.CROSS, CrosshairShape.CROSS_DOT, CrosshairShape.T_SHAPE -> {
                canvas.drawLine(cx, cy - gap, cx, cy - gap - size, paint)          // up
                if (s.shape != CrosshairShape.T_SHAPE) {
                    canvas.drawLine(cx, cy + gap, cx, cy + gap + size, paint)      // down
                }
                canvas.drawLine(cx - gap, cy, cx - gap - size, cy, paint)          // left
                canvas.drawLine(cx + gap, cy, cx + gap + size, cy, paint)          // right
                if (s.shape == CrosshairShape.CROSS_DOT) {
                    drawDot(canvas, paint, cx, cy, dot, expand)
                }
            }
            CrosshairShape.X -> {
                val d = (size + gap) * 0.7071f
                val g = gap * 0.7071f
                canvas.drawLine(cx - g, cy - g, cx - d, cy - d, paint)
                canvas.drawLine(cx + g, cy + g, cx + d, cy + d, paint)
                canvas.drawLine(cx - g, cy + g, cx - d, cy + d, paint)
                canvas.drawLine(cx + g, cy - g, cx + d, cy - d, paint)
            }
            CrosshairShape.DOT -> drawDot(canvas, paint, cx, cy, dot, expand)
            CrosshairShape.CIRCLE -> canvas.drawCircle(cx, cy, size, paint)
            CrosshairShape.CIRCLE_DOT -> {
                canvas.drawCircle(cx, cy, size, paint)
                drawDot(canvas, paint, cx, cy, dot, expand)
            }
            CrosshairShape.CHEVRON -> {
                val p = Path()
                p.moveTo(cx - size, cy + size * 0.6f)
                p.lineTo(cx, cy - size * 0.5f)
                p.lineTo(cx + size, cy + size * 0.6f)
                canvas.drawPath(p, paint)
            }
        }
    }

    private fun drawDot(canvas: Canvas, paint: Paint, cx: Float, cy: Float, radius: Float, expand: Float) {
        val prevStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius + expand, paint)
        paint.style = prevStyle
    }
}
