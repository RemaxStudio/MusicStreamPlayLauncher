package com.amberwave.player.feature.nowplaying

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.amberwave.player.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 播放页唱片（设计稿 FpHZ1）：外圈 100 条刻度、古铜色唱片边缘与两道纹路、圆形封面，
 * 以及沿唱片边缘绘制的播放进度弧与圆点。外圈全部矢量绘制，封面仍由 Glide 解码，不创建额外 Bitmap。
 */
class RecordArtworkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val ringView = RecordRingView(context)
    val artwork = AppCompatImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        background = ContextCompat.getDrawable(context, R.drawable.bg_stage_artwork)
    }

    init {
        addView(ringView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(artwork, LayoutParams(0, 0, Gravity.CENTER))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val artworkSize = (min(w, h) * ARTWORK_RATIO).toInt()
        artwork.layoutParams = (artwork.layoutParams as LayoutParams).apply {
            width = artworkSize
            height = artworkSize
            gravity = Gravity.CENTER
        }
    }

    fun updatePlayback(positionMs: Long, durationMs: Long, isPlaying: Boolean) =
        ringView.updatePlayback(positionMs, durationMs, isPlaying)

    fun stopProgressAnimation() = ringView.stopAnimation()

    private class RecordRingView(context: Context) : android.view.View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arcBounds = RectF()
        private val ticks = Path()
        private val tickColor = ContextCompat.getColor(context, R.color.lp_record_ticks)
        private val rimColor = ContextCompat.getColor(context, R.color.lp_record_rim)
        private val rimStrokeColor = ContextCompat.getColor(context, R.color.lp_record_rim_stroke)
        private val grooveOuterColor = ContextCompat.getColor(context, R.color.lp_record_groove_outer)
        private val grooveInnerColor = ContextCompat.getColor(context, R.color.lp_record_groove_inner)
        private val progressColor = ContextCompat.getColor(context, R.color.lp_on_surface)
        private val motionEnabled =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
        private var basePositionMs = 0L
        private var durationMs = 0L
        private var baseRealtimeMs = 0L
        private var playing = false

        fun updatePlayback(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
            basePositionMs = positionMs.coerceAtLeast(0L)
            this.durationMs = durationMs.coerceAtLeast(0L)
            baseRealtimeMs = SystemClock.elapsedRealtime()
            playing = isPlaying
            postInvalidateOnAnimation()
        }

        fun stopAnimation() {
            playing = false
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val size = min(w, h).toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val outerRadius = size * TICK_OUTER_RATIO
            val innerRadius = size * TICK_INNER_RATIO
            ticks.reset()
            repeat(TICK_COUNT) { index ->
                val angle = Math.toRadians(index * (360.0 / TICK_COUNT) - 90.0)
                val c = cos(angle).toFloat()
                val s = sin(angle).toFloat()
                ticks.moveTo(cx + c * outerRadius, cy + s * outerRadius)
                ticks.lineTo(cx + c * innerRadius, cy + s * innerRadius)
            }
        }

        override fun onDraw(canvas: Canvas) {
            val size = min(width, height).toFloat()
            val cx = width / 2f
            val cy = height / 2f

            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = dp(2.2f)
            paint.color = tickColor
            canvas.drawPath(ticks, paint)

            // 古铜色唱片边缘：半透明填充 + 细描边。
            val rimRadius = size * RIM_RATIO
            paint.style = Paint.Style.FILL
            paint.color = rimColor
            canvas.drawCircle(cx, cy, rimRadius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = rimStrokeColor
            canvas.drawCircle(cx, cy, rimRadius, paint)
            paint.color = grooveOuterColor
            canvas.drawCircle(cx, cy, size * GROOVE_OUTER_RATIO, paint)
            paint.color = grooveInnerColor
            canvas.drawCircle(cx, cy, size * GROOVE_INNER_RATIO, paint)

            val arcRadius = size * PROGRESS_RATIO
            arcBounds.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
            val progress = currentProgress()
            if (progress > 0f) {
                paint.strokeWidth = dp(2.5f)
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = progressColor
                canvas.drawArc(arcBounds, -90f, progress * 360f, false, paint)
            }
            val angle = Math.toRadians((-90f + progress * 360f).toDouble())
            paint.style = Paint.Style.FILL
            paint.color = progressColor
            canvas.drawCircle(
                cx + cos(angle).toFloat() * arcRadius,
                cy + sin(angle).toFloat() * arcRadius,
                dp(4f),
                paint,
            )
            if (playing && motionEnabled && isAttachedToWindow) postInvalidateOnAnimation()
        }

        private fun currentProgress(): Float {
            if (durationMs <= 0L) return 0f
            val elapsed = if (playing) SystemClock.elapsedRealtime() - baseRealtimeMs else 0L
            return ((basePositionMs + elapsed).toFloat() / durationMs).coerceIn(0f, 1f)
        }

        private fun dp(value: Float) = value * resources.displayMetrics.density
    }

    private companion object {
        /** 比例取自 286dp 设计稿：封面 218、边缘 256、纹路 246 / 234、刻度 286→262。 */
        const val ARTWORK_RATIO = 0.762f
        const val TICK_COUNT = 100
        const val TICK_OUTER_RATIO = 0.494f
        const val TICK_INNER_RATIO = 0.458f
        const val RIM_RATIO = 0.4476f
        const val GROOVE_OUTER_RATIO = 0.430f
        const val GROOVE_INNER_RATIO = 0.409f
        const val PROGRESS_RATIO = 0.452f
    }
}
