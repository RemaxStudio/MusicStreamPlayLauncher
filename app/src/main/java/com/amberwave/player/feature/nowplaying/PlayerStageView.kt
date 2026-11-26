package com.amberwave.player.feature.nowplaying

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.NestedScrollView
import com.amberwave.player.R

/**
 * 播放页内容舞台：正面为唱片进度视图，背面为同尺寸的歌词 / 歌曲介绍卡片（设计稿 286dp 方形）。
 * 组件自己管理翻转状态与动画取消，Activity 只传入内容，避免页面层堆积动画细节。
 */
class PlayerStageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val artworkFace = RecordArtworkView(context)
    private val textFace = LayoutInflater.from(context).inflate(
        R.layout.player_text_face,
        this,
        false,
    )
    private val textLabel: TextView = textFace.findViewById(R.id.trackTextLabel)
    private val textContent: TextView = textFace.findViewById(R.id.trackTextContent)
    private val textScroll: NestedScrollView = textFace.findViewById(R.id.trackTextScroll)

    private var flipAnimator: AnimatorSet? = null
    private var animationToken = 0
    private var isAnimating = false
    private var textAvailable = false
    private var textVisible = false
    private var requestedTextVisible = false
    private var pendingTarget: Boolean? = null
    private var showTextDescription: CharSequence = context.getString(R.string.player_show_lyrics)

    /** 用户发起的正反面切换，交给 ViewModel 保存配置变更期间的状态。 */
    var onTextVisibilityChanged: ((Boolean) -> Unit)? = null

    /** 正反面或可用文本变化时回调，供页面同步“显示歌词 ›”提示。 */
    var onFaceStateChanged: (() -> Unit)? = null

    /** 当前歌曲是否提供歌词或介绍（无文本时不显示翻转入口）。 */
    val hasTrackText: Boolean
        get() = textAvailable

    /** 用户最后请求展示的是否为文字面（动画进行中也返回目标面）。 */
    val isShowingTrackText: Boolean
        get() = requestedTextVisible && textAvailable

    /** 文字面的语义：歌词或歌曲介绍。 */
    var isLyricsText: Boolean = true
        private set

    /** Glide 只解码一份封面，背面纯由 View 绘制，不增加 Bitmap 内存。 */
    val artwork: AppCompatImageView
        get() = artworkFace.artwork

    init {
        // 两个面共用同一个居中的正方形，翻转时尺寸与位置保持一致。
        addView(artworkFace, LayoutParams(0, 0, Gravity.CENTER))
        addView(textFace, LayoutParams(0, 0, Gravity.CENTER))

        val distance = resources.displayMetrics.density * CAMERA_DISTANCE_DP
        artworkFace.cameraDistance = distance
        textFace.cameraDistance = distance
        isFocusable = true

        artworkFace.setOnClickListener { performClick() }
        textFace.setOnClickListener { performClick() }
        textScroll.setOnClickListener { performClick() }
        textContent.setOnClickListener { performClick() }
        setFaceImmediately(showText = false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val horizontalMargin = (ARTWORK_HORIZONTAL_MARGIN_DP * resources.displayMetrics.density).toInt()
        val maxArtworkSize = resources.getDimensionPixelSize(R.dimen.player_artwork_max_size)
        val stageSize = minOf(
            maxArtworkSize,
            (width - horizontalMargin * 2).coerceAtLeast(0),
            height.coerceAtLeast(0),
        )
        listOf(artworkFace, textFace).forEach { face ->
            (face.layoutParams as LayoutParams).apply {
                this.width = stageSize
                this.height = stageSize
                gravity = Gravity.CENTER
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** 更新内容时保留 ViewModel 指定的面；切歌时 ViewModel 会先将它重置为封面。 */
    fun setTrackText(
        @StringRes labelRes: Int?,
        content: CharSequence?,
        showText: Boolean,
    ) {
        textAvailable = labelRes != null && !content.isNullOrBlank()
        if (textAvailable) {
            textLabel.setText(labelRes!!)
            textContent.text = content
            isLyricsText = labelRes == R.string.player_lyrics
            showTextDescription = context.getString(
                if (labelRes == R.string.player_lyrics) {
                    R.string.player_show_lyrics
                } else {
                    R.string.player_show_description
                },
            )
            textScroll.post { textScroll.scrollTo(0, 0) }
        } else {
            textContent.text = null
        }
        cancelAnimationAndSetFace(showText && textAvailable)
    }

    fun updatePlayback(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        artworkFace.updatePlayback(positionMs, durationMs, isPlaying)
    }

    fun stopProgressAnimation() {
        artworkFace.stopProgressAnimation()
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (!textAvailable) return false
        requestFace(!requestedTextVisible, animate = true, notify = true)
        return true
    }

    override fun onDetachedFromWindow() {
        // 脱离窗口后立即释放 Animator 对 View 的引用，但保留用户最后请求的面。
        cancelAnimationAndSetFace(requestedTextVisible && textAvailable)
        super.onDetachedFromWindow()
    }

    private fun requestFace(showText: Boolean, animate: Boolean, notify: Boolean) {
        val target = showText && textAvailable
        requestedTextVisible = target
        onFaceStateChanged?.invoke()
        if (notify) onTextVisibilityChanged?.invoke(target)

        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            cancelAnimationAndSetFace(target)
            return
        }
        if (isAnimating) {
            // 快速连点时记住最后意图，当前 330ms 翻转结束后再无缝执行。
            pendingTarget = target
            return
        }
        if (textVisible == target) {
            updateAccessibility()
            return
        }
        startFlip(target)
    }

    private fun startFlip(showText: Boolean) {
        val token = ++animationToken
        val outgoing = if (textVisible) textFace else artworkFace
        val incoming = if (showText) textFace else artworkFace
        val direction = if (showText) 1f else -1f

        isAnimating = true
        outgoing.visibility = View.VISIBLE
        outgoing.rotationY = 0f
        outgoing.alpha = 1f
        incoming.visibility = View.INVISIBLE
        incoming.rotationY = -direction * HALF_TURN_DEGREES
        incoming.alpha = MIDPOINT_ALPHA

        val firstHalf = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(outgoing, View.ROTATION_Y, 0f, direction * HALF_TURN_DEGREES),
                ObjectAnimator.ofFloat(outgoing, View.ALPHA, 1f, MIDPOINT_ALPHA),
            )
            duration = OUTGOING_DURATION_MS
            interpolator = OUTGOING_INTERPOLATOR
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (token != animationToken) return
                    outgoing.visibility = View.INVISIBLE
                    incoming.visibility = View.VISIBLE
                }
            })
        }
        val secondHalf = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(incoming, View.ROTATION_Y, -direction * HALF_TURN_DEGREES, 0f),
                ObjectAnimator.ofFloat(incoming, View.ALPHA, MIDPOINT_ALPHA, 1f),
            )
            duration = INCOMING_DURATION_MS
            interpolator = INCOMING_INTERPOLATOR
        }
        flipAnimator = AnimatorSet().apply {
            playSequentially(firstHalf, secondHalf)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (token != animationToken) return
                    flipAnimator = null
                    isAnimating = false
                    setFaceImmediately(showText)
                    val queuedTarget = pendingTarget.also { pendingTarget = null }
                    if (queuedTarget != null && queuedTarget != textVisible) startFlip(queuedTarget)
                }
            })
            start()
        }
    }

    private fun cancelAnimationAndSetFace(showText: Boolean) {
        animationToken++
        flipAnimator?.cancel()
        flipAnimator = null
        isAnimating = false
        pendingTarget = null
        requestedTextVisible = showText
        setFaceImmediately(showText)
    }

    private fun setFaceImmediately(showText: Boolean) {
        textVisible = showText && textAvailable
        artworkFace.visibility = if (textVisible) View.INVISIBLE else View.VISIBLE
        artworkFace.rotationY = 0f
        artworkFace.alpha = 1f
        textFace.visibility = if (textVisible) View.VISIBLE else View.INVISIBLE
        textFace.rotationY = 0f
        textFace.alpha = 1f
        updateAccessibility()
    }

    private fun updateAccessibility() {
        onFaceStateChanged?.invoke()
        isClickable = textAvailable
        contentDescription = when {
            !textAvailable -> context.getString(R.string.player_artwork)
            textVisible -> context.getString(R.string.player_show_artwork)
            else -> showTextDescription
        }
    }

    private companion object {
        const val HALF_TURN_DEGREES = 90f
        const val MIDPOINT_ALPHA = 0.72f
        const val OUTGOING_DURATION_MS = 145L
        const val INCOMING_DURATION_MS = 185L
        const val CAMERA_DISTANCE_DP = 12_000f
        const val ARTWORK_HORIZONTAL_MARGIN_DP = 44f
        val OUTGOING_INTERPOLATOR = PathInterpolator(0.7f, 0f, 0.84f, 0f)
        val INCOMING_INTERPOLATOR = PathInterpolator(0.16f, 1f, 0.3f, 1f)
    }
}
