package com.amberwave.player.feature.nowplaying

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.amberwave.player.R
import kotlin.math.abs
import kotlin.math.max

/**
 * 播放进度条（设计稿 MusicPlayerFlowLauncher.pen）。轨道在 View 内部为 thumb 预留半径，0% 和 100% 时均不会裁剪圆点。
 */
class PlaybackSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackBounds = RectF()
    private val trackColor = ContextCompat.getColor(context, R.color.lp_seek_track)
    private val activeColor = ContextCompat.getColor(context, R.color.lp_accent)
    private val thumbStrokeColor = ContextCompat.getColor(context, R.color.lp_on_surface)
    private var fraction = 0f
    private var tracking = false
    private var downX = 0f
    private var startFraction = 0f
    private var moved = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    var onSeekStarted: (() -> Unit)? = null
    var onSeekChanged: ((Float) -> Unit)? = null
    var onSeekFinished: ((Float) -> Unit)? = null
    var onSeekCancelled: (() -> Unit)? = null

    init {
        isFocusable = true
        isClickable = true
    }

    fun setProgress(value: Float) {
        if (tracking) return
        fraction = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val radius = dp(THUMB_RADIUS_DP + THUMB_STROKE_DP)
        val centerY = h / 2f
        trackBounds.set(radius, centerY - dp(2f), max(radius, w - radius), centerY + dp(2f))
    }

    /** 设计稿：4dp 半透明轨道、强调色已播放段、带浅色描边的 10dp 强调色圆点。 */
    override fun onDraw(canvas: Canvas) {
        val thumbX = trackBounds.left + trackBounds.width() * fraction
        val corner = dp(2f)
        paint.style = Paint.Style.FILL
        paint.color = trackColor
        canvas.drawRoundRect(trackBounds, corner, corner, paint)

        if (fraction > 0f) {
            paint.color = activeColor
            canvas.drawRoundRect(trackBounds.left, trackBounds.top, thumbX, trackBounds.bottom, corner, corner, paint)
        }
        val cy = height / 2f
        paint.color = activeColor
        canvas.drawCircle(thumbX, cy, dp(THUMB_RADIUS_DP), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(THUMB_STROKE_DP)
        paint.color = thumbStrokeColor
        canvas.drawCircle(thumbX, cy, dp(THUMB_RADIUS_DP), paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                tracking = true
                downX = event.x
                startFraction = fraction
                moved = false
                onSeekStarted?.invoke()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && abs(event.x - downX) > touchSlop) moved = true
                if (moved) updateFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                    val tappedFraction = fractionForX(event.x)
                    // 长按 thumb 但未移动时不发起 seek，避免无意义的网络重新缓冲。
                    if (moved || abs(tappedFraction - startFraction) > MIN_TAP_DELTA) {
                        if (!moved) {
                            fraction = tappedFraction
                            onSeekChanged?.invoke(fraction)
                            invalidate()
                        }
                        onSeekFinished?.invoke(fraction)
                    } else {
                        fraction = startFraction
                        onSeekCancelled?.invoke()
                        invalidate()
                    }
                } else {
                    fraction = startFraction
                    onSeekCancelled?.invoke()
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(x: Float) {
        fraction = fractionForX(x)
        onSeekChanged?.invoke(fraction)
        invalidate()
    }

    private fun fractionForX(x: Float) =
        ((x - trackBounds.left) / trackBounds.width()).coerceIn(0f, 1f)

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val THUMB_RADIUS_DP = 5f
        const val THUMB_STROKE_DP = 1.2f
        const val MIN_TAP_DELTA = 0.005f
    }
}
