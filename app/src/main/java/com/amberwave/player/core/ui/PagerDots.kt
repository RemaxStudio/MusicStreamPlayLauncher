package com.amberwave.player.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.amberwave.player.R

/**
 * 轻量分页圆点指示器（设计稿 7dp 圆点、8dp 间距，选中为强调色）。
 * 只负责绘制，页码由宿主根据 RecyclerView 的 PagerSnapHelper 结果同步。
 */
class PagerDots @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val dotSize = DOT_SIZE_DP * density
    private val dotGap = DOT_GAP_DP * density
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lp_accent)
    }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lp_page_indicator_inactive)
    }

    var pageCount: Int = 0
        set(value) {
            if (field == value) return
            field = value.coerceAtLeast(0)
            currentPage = currentPage.coerceIn(0, (field - 1).coerceAtLeast(0))
            invalidate()
        }

    var currentPage: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            if (field == clamped) return
            field = clamped
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pageCount <= 1) return
        val contentWidth = pageCount * dotSize + (pageCount - 1) * dotGap
        val radius = dotSize / 2f
        val cy = height / 2f
        var cx = (width - contentWidth) / 2f + radius
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        repeat(pageCount) { slot ->
            val index = if (rtl) pageCount - 1 - slot else slot
            canvas.drawCircle(cx, cy, radius, if (index == currentPage) activePaint else inactivePaint)
            cx += dotSize + dotGap
        }
    }

    private companion object {
        const val DOT_SIZE_DP = 7f
        const val DOT_GAP_DP = 8f
    }
}
