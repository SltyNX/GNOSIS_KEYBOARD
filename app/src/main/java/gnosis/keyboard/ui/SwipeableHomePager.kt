package gnosis.keyboard.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

class SwipeableHomePager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 2
    private var velocityTracker: VelocityTracker? = null

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var isBeingDragged = false

    var currentPage = 0
        private set

    var onPageChanged: ((Int) -> Unit)? = null
    var onPageScrollProgress: ((Float) -> Unit)? = null

    private var pageAnimator: ValueAnimator? = null

    init {
        clipChildren = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val height = b - t
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val left = i * width
            child.layout(left, 0, left + width, height)
        }
        if (changed && pageAnimator == null) {
            scrollTo(currentPage * width, 0)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            isBeingDragged = false
            velocityTracker?.recycle()
            velocityTracker = null
            return false
        }

        if (action != MotionEvent.ACTION_DOWN && isBeingDragged) {
            return true
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                if (pageAnimator?.isRunning == true) {
                    pageAnimator?.cancel()
                    isBeingDragged = true
                } else {
                    isBeingDragged = false
                }
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                val xDiff = abs(dx)
                val yDiff = abs(dy)

                if (xDiff > touchSlop && xDiff > yDiff * 1.25f) {
                    val canScrollLeft = dx < 0 && currentPage < childCount - 1
                    val canScrollRight = dx > 0 && currentPage > 0

                    if (canScrollLeft || canScrollRight) {
                        isBeingDragged = true
                        lastX = ev.x
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                velocityTracker?.addMovement(ev)
            }
        }

        return isBeingDragged
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)

        val action = ev.actionMasked
        val width = width

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (pageAnimator?.isRunning == true) {
                    pageAnimator?.cancel()
                }
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isBeingDragged) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    val xDiff = abs(dx)
                    val yDiff = abs(dy)
                    if (xDiff > touchSlop && xDiff > yDiff * 1.25f) {
                        isBeingDragged = true
                        lastX = ev.x
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                if (isBeingDragged && width > 0) {
                    val deltaX = lastX - ev.x
                    lastX = ev.x

                    val maxScroll = (childCount - 1) * width
                    val newScrollX = (scrollX + deltaX).coerceIn(0f, maxScroll.toFloat()).toInt()
                    scrollTo(newScrollX, 0)

                    val progress = newScrollX.toFloat() / width.coerceAtLeast(1)
                    onPageScrollProgress?.invoke(progress)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isBeingDragged) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val targetPage: Int

                    if (abs(vx) > minFlingVelocity) {
                        targetPage = if (vx < 0) {
                            (currentPage + 1).coerceAtMost(childCount - 1)
                        } else {
                            (currentPage - 1).coerceAtLeast(0)
                        }
                    } else {
                        val currentProgress = if (width > 0) scrollX.toFloat() / width else 0f
                        targetPage = if (currentProgress >= 0.45f) 1 else 0
                    }

                    smoothScrollToPage(targetPage)
                    isBeingDragged = false
                }
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isBeingDragged) {
                    smoothScrollToPage(currentPage)
                    isBeingDragged = false
                }
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    fun smoothScrollToPage(page: Int, durationMs: Long = 280L) {
        val targetPage = page.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        val targetX = targetPage * width
        val startX = scrollX

        if (startX == targetX) {
            if (currentPage != targetPage) {
                currentPage = targetPage
                onPageChanged?.invoke(targetPage)
            }
            return
        }

        pageAnimator?.cancel()
        pageAnimator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                val v = anim.animatedValue as Int
                scrollTo(v, 0)
                if (width > 0) {
                    onPageScrollProgress?.invoke(v.toFloat() / width)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentPage = targetPage
                    pageAnimator = null
                    onPageChanged?.invoke(targetPage)
                }
            })
            start()
        }
    }

    fun setCurrentPageImmediate(page: Int) {
        pageAnimator?.cancel()
        currentPage = page.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        if (width > 0) {
            scrollTo(currentPage * width, 0)
            onPageScrollProgress?.invoke(currentPage.toFloat())
        }
        onPageChanged?.invoke(currentPage)
    }
}
