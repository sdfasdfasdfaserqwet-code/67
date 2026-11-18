package dev.lite.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Корневой контейнер: список иконок + затемнение + шторка.
 * Свайп вниз (когда список прокручен до верха) тянет шторку за пальцем,
 * свайп вверх закрывает. Анимация идёт только через translationY/alpha
 * с аппаратным слоем, поэтому GPU делает всю работу.
 */
class ShadeHost(ctx: Context) : FrameLayout(ctx) {
    private lateinit var list: RecyclerView
    private lateinit var panel: View
    private lateinit var scrim: View

    var onShow: (() -> Unit)? = null

    var progress = 0f
        private set
    val isOpen: Boolean get() = progress > 0f

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val panelH: Int get() = panel.height.coerceAtLeast(1)

    private var downX = 0f
    private var downY = 0f
    private var startProgress = 0f
    private var dragging = false
    private var vt: VelocityTracker? = null
    private var anim: ValueAnimator? = null

    fun bind(list: RecyclerView, panel: View, scrim: View) {
        this.list = list
        this.panel = panel
        this.scrim = scrim
        panel.isClickable = true
        setProgress(0f)
    }

    fun setProgress(p: Float) {
        val old = progress
        progress = p.coerceIn(0f, 1f)
        panel.translationY = -(1f - progress) * panelH
        scrim.alpha = progress
        val vis = if (progress > 0f) VISIBLE else INVISIBLE
        if (panel.visibility != vis) {
            panel.visibility = vis
            scrim.visibility = vis
        }
        if (old == 0f && progress > 0f) onShow?.invoke()
    }

    fun open() = animateTo(1f)

    fun close(animated: Boolean = true) {
        if (animated) {
            animateTo(0f)
        } else {
            anim?.cancel()
            setProgress(0f)
        }
    }

    private fun animateTo(target: Float) {
        anim?.cancel()
        panel.setLayerType(LAYER_TYPE_HARDWARE, null)
        val a = ValueAnimator.ofFloat(progress, target)
        a.duration = (280 * abs(target - progress)).toLong().coerceAtLeast(100L)
        a.interpolator = DecelerateInterpolator(1.5f)
        a.addUpdateListener { setProgress(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                panel.setLayerType(LAYER_TYPE_NONE, null)
            }
        })
        anim = a
        a.start()
    }

    private fun initDown(e: MotionEvent) {
        downX = e.x
        downY = e.y
        dragging = false
        vt?.recycle()
        vt = VelocityTracker.obtain().also { it.addMovement(e) }
    }

    private fun maybeStartDrag(e: MotionEvent): Boolean {
        val dy = e.y - downY
        val dx = e.x - downX
        if (abs(dy) <= slop || abs(dy) <= abs(dx) * 1.5f) return false
        val ok = (dy > 0 && progress < 1f && !list.canScrollVertically(-1)) ||
            (dy < 0 && progress > 0f)
        if (!ok) return false
        anim?.cancel()
        dragging = true
        startProgress = progress
        downY = e.y
        panel.setLayerType(LAYER_TYPE_HARDWARE, null)
        return true
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> initDown(e)
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (!dragging) return maybeStartDrag(e)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.recycle()
                vt = null
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> initDown(e)
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (!dragging) maybeStartDrag(e)
                if (dragging) setProgress(startProgress + (e.y - downY) / panelH)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.addMovement(e)
                if (dragging) {
                    vt?.computeCurrentVelocity(1000)
                    val vy = vt?.yVelocity ?: 0f
                    val target = when {
                        vy > 900f -> 1f
                        vy < -900f -> 0f
                        progress > 0.5f -> 1f
                        else -> 0f
                    }
                    animateTo(target)
                }
                dragging = false
                vt?.recycle()
                vt = null
            }
        }
        return true
    }
}
