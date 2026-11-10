package com.luc.body.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import com.luc.body.state.EmotionMetrics
import java.util.Locale

internal const val EMOTION_METRICS_WIDTH_DP = 88
internal const val EMOTION_METRICS_HEIGHT_DP = 88
internal const val EMOTION_METRICS_SCALE = 88f / 112f
internal const val EMOTION_METRICS_PANEL_INSET_DP = 3f * EMOTION_METRICS_SCALE
internal const val EMOTION_METRICS_GAP_DP = 20

class EmotionMetricsPanelView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(scaledDp(7f), 0f, scaledDp(3f), Color.argb(42, 178, 83, 115))
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(143, 82, 102)
        textSize = scaledSp(10f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(128, 80, 97)
        textSize = scaledSp(8.5f)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(176, 110, 132)
        textSize = scaledSp(8.5f)
        textAlign = Paint.Align.RIGHT
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(42, 202, 144, 163)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(38, 239, 143, 171)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(239, 143, 171)
    }
    private val panelRect = RectF()
    private val trackRect = RectF()
    private val fillRect = RectF()
    private var metrics: EmotionMetrics? = null
    private var rows = emptyList<MetricRow>()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        visibility = INVISIBLE
    }

    fun render(metrics: EmotionMetrics?) {
        if (this.metrics == metrics && visibility == if (metrics == null) INVISIBLE else VISIBLE) return
        this.metrics = metrics
        rows = metrics?.let(::rowsFor).orEmpty()
        visibility = if (metrics == null) INVISIBLE else VISIBLE
        contentDescription = metrics?.let(::description)
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        panelRect.set(
            scaledDp(3f),
            scaledDp(3f),
            width - scaledDp(3f),
            height - scaledDp(3f),
        )
        panelPaint.shader = LinearGradient(
            panelRect.left,
            panelRect.top,
            panelRect.right,
            panelRect.bottom,
            intArrayOf(Color.rgb(255, 250, 252), Color.rgb(255, 235, 243)),
            null,
            Shader.TileMode.CLAMP,
        )
        fillPaint.shader = LinearGradient(
            scaledDp(13f),
            0f,
            width - scaledDp(13f),
            0f,
            Color.rgb(247, 184, 202),
            Color.rgb(236, 128, 159),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (metrics == null) return
        canvas.drawRoundRect(panelRect, scaledDp(14f), scaledDp(14f), panelPaint)
        canvas.drawText("情绪状态", scaledDp(13f), scaledDp(21f), titlePaint)
        canvas.drawCircle(
            width - scaledDp(15f),
            scaledDp(16f),
            scaledDp(6f),
            haloPaint,
        )
        canvas.drawCircle(
            width - scaledDp(15f),
            scaledDp(16f),
            scaledDp(3f),
            dotPaint,
        )
        rows.forEachIndexed { index, row -> drawRow(canvas, row, index) }
    }

    private fun drawRow(canvas: Canvas, row: MetricRow, index: Int) {
        val labelBaseline = scaledDp(41f + index * 24f)
        val trackTop = scaledDp(46f + index * 24f)
        val left = scaledDp(13f)
        val right = width - scaledDp(13f)
        trackRect.set(left, trackTop, right, trackTop + scaledDp(4f))
        canvas.drawText(row.label, left, labelBaseline, labelPaint)
        canvas.drawText(row.value ?: "—", right, labelBaseline, valuePaint)
        canvas.drawRoundRect(trackRect, scaledDp(2.5f), scaledDp(2.5f), trackPaint)

        val ratio = row.ratio?.coerceIn(0f, 1f) ?: return
        if (ratio <= 0f) return
        fillRect.set(left, trackTop, left + trackRect.width() * ratio, trackRect.bottom)
        canvas.drawRoundRect(fillRect, scaledDp(2.5f), scaledDp(2.5f), fillPaint)
    }

    private fun rowsFor(metrics: EmotionMetrics): List<MetricRow> = listOf(
        MetricRow("心情", metrics.valence?.let(::formatDecimal), metrics.valence?.toFloat()),
        MetricRow("活力", metrics.arousal?.let(::formatDecimal), metrics.arousal?.toFloat()),
        MetricRow("亲密", metrics.heat?.let { "$it / 10" }, metrics.heat?.div(10f)),
    )

    private fun description(metrics: EmotionMetrics): String = listOf(
        "心情 ${metrics.valence?.let(::formatDecimal) ?: "暂无"}",
        "活力 ${metrics.arousal?.let(::formatDecimal) ?: "暂无"}",
        "亲密 ${metrics.heat?.let { "$it / 10" } ?: "暂无"}",
    ).joinToString(prefix = "情绪状态，", separator = "，")

    private fun formatDecimal(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun dp(value: Float): Float = value * density

    private fun scaledDp(value: Float): Float = dp(value * EMOTION_METRICS_SCALE)

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics,
    )

    private fun scaledSp(value: Float): Float = sp(value * EMOTION_METRICS_SCALE)

    private data class MetricRow(
        val label: String,
        val value: String?,
        val ratio: Float?,
    )
}
