package com.ggg.ui.banner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.util.SparseIntArray
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

class CardsBannerView(context: Context, attributeSet: AttributeSet?, styleRes: Int) :
    FrameLayout(context, attributeSet, styleRes) {

    constructor(context: Context) : this(context, null, -1)
    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, -1)

    companion object {
        private const val AUTO_SCROLL_MSG = 1
        private const val FLING_CALCULATE_MSG = 2

        private const val MIN_VISIBLE_COUNT = 3
        private const val MIN_ALPHA = 0f
        private const val MAX_ALPHA = 1.0f
        private const val DEFAULT_FLING_VELOCITY = 0.5f
        private const val UNIT_FOR_VELOCITY_TRACKER = 1000

        private const val STOP_AUTO_TYPE_USER = 0
        private const val STOP_AUTO_TYPE_INTERNAL = 1
        private const val STOP_AUTO_TYPE_SCREEN_OFF = 2
        private const val STOP_AUTO_TYPE_DETACH = 3
        private const val STOP_AUTO_TYPE_NONE = 4

        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2

    }

    private lateinit var viewConfiguration: ViewConfiguration
    private lateinit var autoHandler: Handler
    private var layerCount = 0
    private var totalCount = 0
    private var currentNearlyIndex: Float = 0f
    private var layerAlphaDiff: Float = 0f
    private var childWidth: Int = 0
    private var childHeight: Int = 0
    private var topLayerOriginTranslateX: Int = 0
    private var layerTransXDiffer: Float = 0f
    private var velocity: VelocityTracker? = null
    private var isAnimatorChildToTopRunning = false
    private var startSpeed = 0f
    private var acceleration: Float = 0f
    private var duration = 0f
    private var startTime = 0L
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var hasAnimatorWhenDownEvent: Boolean = false
    private var scrollState: Int = SCROLL_STATE_IDLE
    private lateinit var idleTopLayerChild: View
    private var offset: Float = 0f
    private var lastOffset: Float = 0f
    private var downOffset: Float = 0f
    private var initLayout = true
    private var stopAutoScrollType: Int = STOP_AUTO_TYPE_NONE
    private var newIndex = SparseIntArray()
    private var leftPos: Int = 0 //??????????????????????????????
    private var rightPos: Int = 0
    private var selectRealPosition: Int = -1
    private var screenReceiver: BroadcastReceiver? = null
    private val occupiedViewHolders = mutableMapOf<View, CardsBannerViewHolder>()
    private val recycledViewHolders = mutableListOf<CardsBannerViewHolder>()

    private var adapter: CardsBannerAdapter? = null
    private var onLayerChangeListener: OnLayerChangeListener? = null
    private var visibleCount: Int = 0
    private var levelOneOutOffsetRatio: Float = 0f //?????????????????????????????????????????????
    private var minScale: Float = 0f
    private var maxAlpha: Float = 0f
    private var minAlpha: Float = 0f
    private var layerScaleDiff: Float = 0f
    private var scrollVelocity = 0.0f //???????????????????????????????????????card??????
    private var flingVelocity = 0.0f //fling???????????????????????????????????????card??????,????????????0-1
    private var autoScrollFrequency = 0L
    private var swapLayerVelocity = 0L //???????????????????????????????????????
    private var stopAutoScrollScreenOff: Boolean = false

    init {
        init(attributeSet)
    }

    private fun init(attributeSet: AttributeSet?) {
        viewConfiguration = ViewConfiguration.get(context)
        autoHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    AUTO_SCROLL_MSG -> {
                        if (isLayout()) {
                            animatorChildToTop(getChildAt(childCount - 1 - 1))
                            startAutoScroll()
                        }
                    }
                    FLING_CALCULATE_MSG -> {
                        calculateFling(msg.obj as Float)
                    }
                }
            }
        }
        initAttribute(attributeSet)
        reset(false)
    }

    private fun initAttribute(attributeSet: AttributeSet?) {
        val attributes = context.obtainStyledAttributes(attributeSet, R.styleable.CardsBannerView)
        maxAlpha = attributes.getFloat(R.styleable.CardsBannerView_maxAlpha, MAX_ALPHA)
        maxAlpha.coerceIn(MIN_ALPHA * 2, MAX_ALPHA)
        minAlpha = attributes.getFloat(R.styleable.CardsBannerView_minAlpha, MIN_ALPHA)
        minAlpha.coerceIn(MIN_ALPHA, maxAlpha / 2)
        minScale = attributes.getFloat(R.styleable.CardsBannerView_minScale, 0.2f)
        layerScaleDiff = attributes.getFloat(R.styleable.CardsBannerView_layerScale, 0.2f)
        autoScrollFrequency =
            attributes.getInt(R.styleable.CardsBannerView_autoScrollFrequency, 3000).toLong()
        scrollVelocity = attributes.getFloat(R.styleable.CardsBannerView_scrollVelocity, 1.0f)
        flingVelocity =
            attributes.getFloat(R.styleable.CardsBannerView_flingVelocity, DEFAULT_FLING_VELOCITY)
        swapLayerVelocity =
            attributes.getInt(R.styleable.CardsBannerView_swapLayerVelocity, 300).toLong()
        //fling???????????????
        val coefficientFriction =
            attributes.getFloat(R.styleable.CardsBannerView_coefficientFriction, 1.0f)
        //??????????????????
        acceleration = coefficientFriction * 9.8f
        levelOneOutOffsetRatio =
            attributes.getFloat(R.styleable.CardsBannerView_levelOneOutOffsetRatio, 0f)
        stopAutoScrollScreenOff =
            attributes.getBoolean(R.styleable.CardsBannerView_stopAutoScrollScreenOff, false)
        //???????????????????????????
        visibleCount = attributes.getInt(R.styleable.CardsBannerView_visibleCount, 5)
        initCounts()
        layerAlphaDiff = initLayerAlphaDiff()
        layerScaleDiff = initLayerScaleDiff()
        attributes.recycle()
        isClickable = true
    }

    private fun initCounts() {
        //?????????????????????????????????????????????????????????
        if (visibleCount % 2 == 0) {
            visibleCount++
        }
        totalCount = visibleCount + 2
        layerCount = (totalCount + 1) shr 1
    }

    public fun setVisibleCount(visibleCount: Int) {
        if (this.visibleCount == visibleCount) {
            if (adapter != null && adapter!!.getCount() != 0) {
                resetSize()
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    val param = getChildAt(i).layoutParams
                    param.width = childWidth
                    param.height = childHeight
                    child.layoutParams = param
                    child.requestLayout()
                }
            }
            return
        }
        this.visibleCount = visibleCount
        initCounts()
        resetSize()
        if (adapter != null && adapter!!.getCount() != 0) {
            onDataSetChanged()
        }
    }

    private fun resetSize() {
        layerAlphaDiff = initLayerAlphaDiff()
        layerScaleDiff = initLayerScaleDiff()
        val width = context.resources.displayMetrics.widthPixels
        val sizeDivider = 2 + (visibleCount - 3) * 0.5
        val size: Int = (width / sizeDivider).toInt()
        childWidth = size
        childHeight = size
    }

    private fun initLayerAlphaDiff(): Float {
        //?????????alpha?????????
        return (maxAlpha - minAlpha) / (layerCount - 1f)
    }

    private fun initLayerScaleDiff(): Float {
        //??????????????????????????????????????????minScale
        if (1.0f - layerScaleDiff * (layerCount - 1) < minScale) {
            return (1.0f - minScale) / (layerCount - 1)
        }
        return layerScaleDiff
    }

    private fun reset(keepRecycled: Boolean) {
        removeAllViews()
        occupiedViewHolders.onEach {
            onUnbindViewHolder(getAdapterPositionForHolder(it.value), it.value)
        }
        if (!keepRecycled) {
            recycledViewHolders.clear()
        }
        occupiedViewHolders.clear()
        setWillNotDraw(false)
        selectRealPosition = 0
        leftPos = 0
        rightPos = 0
        currentNearlyIndex = 0f
        offset = 0f
        stopAutoScrollType = STOP_AUTO_TYPE_NONE
        initLayout = true
        resetAllAnimator()
    }

    private fun isLayout(): Boolean {
        return adapter?.getCount() ?: 0 > 0 && childCount != 0
    }

    private fun initScreenReceiver() {
        if (screenReceiver == null) {
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action
                    if (action == Intent.ACTION_SCREEN_ON) {
                        if (stopAutoScrollType == STOP_AUTO_TYPE_SCREEN_OFF) {
                            startAutoScroll()
                        }
                    } else if (action == Intent.ACTION_SCREEN_OFF) {
                        if (stopAutoScrollScreenOff && autoHandler.hasMessages(AUTO_SCROLL_MSG)) {
                            stopAutoScroll(STOP_AUTO_TYPE_SCREEN_OFF)
                        }
                    }
                }
            }
        }
    }

    private fun calculateTopLayerTranslateX(): Int {
        return (width - paddingLeft - paddingRight - childWidth) / 2
    }

    private fun calculateLayerTransXDiffer(): Float {
        return if (levelOneOutOffsetRatio == 0f) {
            topLayerOriginTranslateX / (layerCount - 1 - 1).toFloat()
        } else {
            //???????????????1???view????????????,???????????????????????????1???view?????????????????? * outOffsetRatio
            val width = (1.0f - layerScaleDiff * (layerCount - 1 - 1)) * childWidth
            val outOffset = width * levelOneOutOffsetRatio
            (topLayerOriginTranslateX + outOffset) / (layerCount - 1 - 1)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        topLayerOriginTranslateX = calculateTopLayerTranslateX()
        layerTransXDiffer = calculateLayerTransXDiffer()
        if (adapter?.getCount() ?: 0 < MIN_VISIBLE_COUNT) {
            return
        }
        if (childCount == 0) {
            return
        }
        //????????????????????????????????????
        val nearlyIndex = nearlyIndexBy(offset)
        if (!initLayout) {
            val children = mutableListOf<View>()
            newIndex.clear()
            for (i in 0 until childCount) {
                children.add(getChildAt(i))
            }
            val topLayerIndex = children.size - 1
            val bottomLayerLeftIndex = 0
            val bottomLayerRightIndex = 1
            if (currentNearlyIndex + 1.0f == nearlyIndex) {
                children.forEachIndexed { index, view ->
                    if (index % 2 == 0) {
                        if (index == bottomLayerLeftIndex) {
                            //????????????????????????view
                            val removeHolder = occupiedViewHolders[view]!!
                            removeView(view)
                            onUnbindViewHolder(leftPos, removeHolder)
                            occupiedViewHolders.remove(removeHolder.holdView)
                            //??????adapterPosition??????
                            val position = getAdapterPositionForChild(
                                layerCount,
                                bottomLayerRightIndex,
                                nearlyIndex.toInt()
                            )
                            rightPos = position
                            leftPos -= 1
                            if (leftPos == -1) {
                                leftPos = adapter!!.getCount() - 1
                            }
                            //?????????????????????????????????????????????????????????????????????view
                            //?????????????????????index????????????children
                            val newHolder = onCreateViewHolder(position)
                            occupiedViewHolders[newHolder.holdView] = newHolder
                            addView(newHolder.holdView, bottomLayerLeftIndex)
                            children[bottomLayerLeftIndex] = newHolder.holdView
                            newIndex.put(bottomLayerRightIndex, index)
                            onBindViewHolder(position, newHolder)
                        } else {
                            newIndex.put(index - 2, index)
                        }
                    } else {
                        if (index == topLayerIndex - 1) {
                            newIndex.put(index + 1, index)
                        } else {
                            newIndex.put(index + 2, index)
                        }
                    }
                }
                reorderChildren(newIndex, children, true, nearlyIndex.toInt())
            } else if (currentNearlyIndex - 1 == nearlyIndex) {
                children.forEachIndexed { index, view ->
                    if (index % 2 != 0) {
                        if (index == bottomLayerRightIndex) {
                            val removeHolder = occupiedViewHolders[view]!!
                            removeView(view)
                            onUnbindViewHolder(rightPos, removeHolder)
                            occupiedViewHolders.remove(removeHolder.holdView)
                            val position =
                                getAdapterPositionForChild(layerCount, 0, nearlyIndex.toInt())
                            leftPos = position
                            rightPos -= 1
                            if (rightPos == -1) {
                                rightPos = adapter!!.getCount() - 1
                            }
                            val newHolder = onCreateViewHolder(position)
                            occupiedViewHolders[newHolder.holdView] = newHolder
                            addView(newHolder.holdView, bottomLayerRightIndex)
                            children[bottomLayerRightIndex] = newHolder.holdView
                            newIndex.put(bottomLayerRightIndex, index)
                            onBindViewHolder(position, newHolder)
                        } else {
                            newIndex.put(index - 2, index)
                        }
                    } else {
                        if (index + 2 >= children.size) {
                            //????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????view??????
                            newIndex.put(index - 1, index)
                        } else {
                            newIndex.put(index + 2, index)
                        }
                    }
                }
                reorderChildren(newIndex, children, false, nearlyIndex.toInt())
            }
        }
        initLayout = false
        //????????????????????????????????????????????????????????????
        val newPositionOffset = offset - nearlyIndex
        for (i in 0 until layerCount) {
            layoutChildrenLayerByLayer(i, newPositionOffset)
        }
        if (lastOffset != offset) {
            onLayerScrolled(offset - lastOffset > 0)
        }
        lastOffset = offset
        currentNearlyIndex = nearlyIndex
    }

    private fun onLayerScrolled(forward: Boolean) {
        val progress = if (forward) {
            offset - floor(offset)
        } else {
            -(1.0f - (offset - floor(offset)))
        }
        if (progress == 0f || progress == 1f) {
            idleTopLayerChild = getChildAt(childCount - 1)
            Log.d("ggg", "idleTopLayerChild = " + indexOfChild(idleTopLayerChild))
        }
        val holder = occupiedViewHolders[idleTopLayerChild]
        if (holder != null) {
            val adapterPosition = getAdapterPositionForHolder(holder)
            onLayerChangeListener?.onLayerScrolled(adapterPosition, progress)
        }
    }

    private fun reorderChildren(
        newIndex: SparseIntArray,
        children: MutableList<View>,
        forward: Boolean,
        nearlyIndex: Int
    ) {
        for (i in 0 until newIndex.size()) {
            bringChildToFront(children[newIndex[newIndex.keyAt(i)]])
        }
        val topLayerChild = getChildAt(childCount - 1)
        val adapterPosition = getAdapterPositionForChild(layerCount, childCount - 1, nearlyIndex)
        val lastSelectLayer = selectRealPosition
        selectRealPosition = adapterPosition

        onLayerChangeListener?.onLayerTop(
            adapterPosition,
            occupiedViewHolders[topLayerChild]!!
        )
        val lastTopLayerChild = if (forward) {
            getChildAt(childCount - 1 - 2)
        } else {
            getChildAt(childCount - 1 - 1)
        }
        onLayerChangeListener?.onLayerBack(
            lastSelectLayer,
            occupiedViewHolders[lastTopLayerChild]!!
        )
    }

    private fun layoutChildrenLayerByLayer(layerIndex: Int, newPositionOffset: Float) {

        val topLayerIndex = layerCount - 1
        val layerIndexDiffer = topLayerIndex - layerIndex
        //???????????????????????????????????????1???????????????????????????layerScale
        val baseAlpha = maxAlpha - layerIndexDiffer * layerAlphaDiff
        val baseScale = 1.0f - layerIndexDiffer * layerScaleDiff

        val top = paddingTop
        val bottom = childHeight + paddingTop
        val translationXByOffsetToNearlyIndex = -layerTransXDiffer * newPositionOffset

        //???????????????????????????????????????view??????left??? ?????????layerIndex * 2 + 1??????????????????
        val leftChild = getChildAt(layerIndex * 2)
        focusableViewAvailable(leftChild)
        //????????????????????????offset??????0-0.5??????nearlyIndex = 0??? ??????newPositionOffset???0?????????0.5????????????????????????view??????????????????view?????????
        //???offset=0.5??????nearlyIndex = 1???????????????view???????????????newPositionOffset = 0.5????????????????????????view????????????????????????????????????????????????????????????view????????????????????????????????????????????????????????????view????????????????????????????????????
        //???offset??????0.5-1??????nearlyIndex = 1???newPositionOffset???-0.5?????????0???????????????view??????????????????????????????view????????????
        //??????????????????leftScale??????????????????leftAlpha?????????
        val leftScale = if (layerIndex != topLayerIndex) {
            baseScale - layerScaleDiff * newPositionOffset
        } else {
            baseScale - abs(layerScaleDiff * newPositionOffset)
        }
        val leftAlpha = if (layerIndex != topLayerIndex) {
            baseAlpha - layerAlphaDiff * newPositionOffset
        } else {
            baseAlpha - abs(layerAlphaDiff * newPositionOffset)
        }
        leftChild.alpha = leftAlpha
        leftChild.scaleX = leftScale
        leftChild.scaleY = leftScale
        //????????????view???????????????????????????newPositionOffset > 0??????????????????????????????????????????????????????
        leftChild.pivotX = if (layerIndex != topLayerIndex) 0f else {
            if (newPositionOffset > 0) 0f else childWidth.toFloat()
        }
        leftChild.pivotY = (bottom - top) / 2.toFloat()

        //???????????????????????????????????????1???????????????????????????layerTransDiffer????????????????????????????????????????????????????????????
        val leftTranslationX =
            topLayerOriginTranslateX - layerIndexDiffer * layerTransXDiffer + translationXByOffsetToNearlyIndex
        leftChild.translationX = leftTranslationX
        leftChild.layout(paddingLeft, top, childWidth + paddingLeft, bottom)
        if (layerIndex != topLayerIndex) {
            val rightChild = getChildAt(layerIndex * 2 + 1)
            focusableViewAvailable(rightChild)
            //?????????view???????????????????????????????????????offset???0?????????0.5????????????????????????????????????0.5??????????????????
            val rightScale = baseScale + layerScaleDiff * newPositionOffset
            val rightAlpha = baseAlpha + layerAlphaDiff * newPositionOffset
            val rightTranslationX =
                topLayerOriginTranslateX + layerIndexDiffer * layerTransXDiffer + translationXByOffsetToNearlyIndex
            rightChild.alpha = rightAlpha
            rightChild.scaleX = rightScale
            rightChild.scaleY = rightScale
            //?????????child?????????child???????????????
            rightChild.pivotX = childWidth.toFloat()
            rightChild.pivotY = (bottom - top) / 2.toFloat()
            rightChild.translationX = rightTranslationX
            rightChild.layout(paddingLeft, top, childWidth + paddingLeft, bottom)
        }
    }

    private fun nearlyIndexBy(offset: Float): Float {
        //?????????????????????????????????????????????view??????????????????????????????????????????0.5???????????????????????????????????????
        //??????offset??????????????????0.5????????????????????????????????????????????????
        return floor(offset + 0.5f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (stopAutoScrollScreenOff) {
            initScreenReceiver()
            try {
                val filter = IntentFilter()
                filter.addAction(Intent.ACTION_SCREEN_ON)
                filter.addAction(Intent.ACTION_SCREEN_OFF)
                context.registerReceiver(screenReceiver, filter)
            } catch (ignored: Exception) {

            }
        }
        if (stopAutoScrollType == STOP_AUTO_TYPE_DETACH) {
            startAutoScroll()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (screenReceiver != null) {
            try {
                context.unregisterReceiver(screenReceiver)
            } catch (ignored: Exception) {

            }
        }
        stopFlingCalculate(true)
        if (autoHandler.hasMessages(AUTO_SCROLL_MSG)) {
            stopAutoScroll(STOP_AUTO_TYPE_DETACH)
        }
    }

    private fun updateOffset(offset: Float) {
        this.offset = offset
        invalidate()
        requestLayout()
    }

    private fun updateScrollState(scrollState: Int) {
        if (!isLayout()) {
            return
        }
        if (this.scrollState != scrollState) {
            this.scrollState = scrollState
            if (scrollState == SCROLL_STATE_IDLE) {
                idleTopLayerChild = getChildAt(childCount - 1)
            }
            onLayerChangeListener?.onLayerScrollStateChanged(scrollState)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (childCount == 0) {
            return super.onInterceptTouchEvent(event)
        }
        // 1. ?????????????????????view?????????????????????
        // 2. ???????????????????????????????????????????????????
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val child = getChildAt(childCount - 1)
                val isEventInTopView = isEventInView(child, event)
                //????????????????????????onTouchEvent????????????down????????????????????? onDown(event) ???????????????
                //???????????????????????????view??????????????????view?????????????????????????????????????????????????????????????????????view????????????view
                onDown(event)
                return !isEventInTopView || !child.isClickable
            }
            MotionEvent.ACTION_MOVE -> {
                val dx: Float = abs(event.x - downX)
                val dy: Float = abs(event.y - downY)
                if (dx > dy) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val isEventInTopView = isEventInView(getChildAt(childCount - 1), event)
                val isMoved = isMoved(event)
                val isTransState = offset - floor(offset) != 0.0f
//                Log.d("ggg", "isEventInTopView = " + isEventInTopView + ", isMoved = " + isMoved)
                if (isMoved || !isEventInTopView) {
                    return true
                }
                if (!isMoved && isTransState) {
                    upFling(event)
                }
                return false
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                onMove(event)
            }
            MotionEvent.ACTION_UP -> {
                val isMove = isMoved(event)
                onUp(event, isMove)
                checkIfInterceptUpEventBringChildTop(event, isMove)
            }
            MotionEvent.ACTION_CANCEL -> {
                hasAnimatorWhenDownEvent = false
                onUp(event, isMoved(event))
            }
        }
        return true
    }

    private fun isMoved(event: MotionEvent): Boolean {
        val dx: Float = abs(event.x - downX)
        val dy: Float = abs(event.y - downY)
        if (dx > viewConfiguration.scaledTouchSlop || dy > viewConfiguration.scaledTouchSlop) {
            return true
        }
        return false
    }

    private fun checkIfInterceptUpEventBringChildTop(event: MotionEvent, isMove: Boolean) {
        //??????????????????????????????????????????up???????????????????????????view?????????????????????
        if (isMove) {
            hasAnimatorWhenDownEvent = false
            return
        }
        val isEventInTopView = isEventInView(getChildAt(childCount - 1), event)
        if (isEventInTopView) {
            hasAnimatorWhenDownEvent = false
            return
        }
        if (!hasAnimatorWhenDownEvent) {
            //????????????up???????????????????????????????????????fling?????????????????????
            for (i in childCount - 1 - 1 downTo 0) {
                val child = getChildAt(i)
                val isInView = isEventInView(child, event)
                if (isInView) {
                    animatorChildToTop(child)
                    break
                }
            }
        }
        hasAnimatorWhenDownEvent = false
    }

    private fun isEventInView(view: View, ev: MotionEvent): Boolean {
        val frame = Rect()
        view.getHitRect(frame)
        return frame.contains((ev.x + 0.5).toInt(), (ev.y + 0.5).toInt())
    }

    private fun onDown(event: MotionEvent) {
        hasAnimatorWhenDownEvent = autoHandler.hasMessages(FLING_CALCULATE_MSG)
        stopFlingCalculate(false)
        if (autoHandler.hasMessages(AUTO_SCROLL_MSG)) {
            stopAutoScroll(STOP_AUTO_TYPE_INTERNAL)
        }
        downX = event.x
        downY = event.y
        downOffset = offset
        velocity = VelocityTracker.obtain()
        velocity!!.addMovement(event)
        updateScrollState(SCROLL_STATE_IDLE)
    }

    private fun getBaseOffsetWidth(): Float {
        //????????? ??????????????????child?????????????????????offset??????1????????????????????????view
        return childWidth / 2f
    }

    private fun onMove(event: MotionEvent) {
        val dx: Float = abs(event.x - downX)
        val dy: Float = abs(event.y - downY)
        if (dx > dy) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val movedOffset = (event.x - downX) / getBaseOffsetWidth() * scrollVelocity
        //???movedOffset??????0?????????????????????????????????????????????????????????????????????????????????
        updateOffset(downOffset - movedOffset)
        velocity!!.addMovement(event)
        updateScrollState(SCROLL_STATE_DRAGGING)
    }

    private fun onUp(event: MotionEvent, isMove: Boolean) {
        if (!isMove) {
            val isEventInTopView = isEventInView(getChildAt(childCount - 1), event)
//        Log.d("ggg", "onUp move = " + isMove + ", isEventInTopView = " + isEventInTopView)
            if (isEventInTopView) {
                getChildAt(childCount - 1).performClick()
            }
        }
        if (isMove || offset - floor(offset) != 0.0f) {
            upFling(event)
        } else {
            if (stopAutoScrollType == STOP_AUTO_TYPE_INTERNAL) {
                startAutoScroll()
            }
        }
        velocity!!.clear()
        velocity!!.recycle()
    }

    private fun upFling(event: MotionEvent) {
        //??????????????????????????????????????????????????????????????????????????????????????????
        val movedTotalOffsetWhenUp = (event.x - downX) / getBaseOffsetWidth() * scrollVelocity
        velocity!!.addMovement(event)
        velocity!!.computeCurrentVelocity(UNIT_FOR_VELOCITY_TRACKER)
        var v0 = velocity!!.xVelocity
        //?????????????????????????????????????????????????????????flingVelocity??????
        v0 /= getBaseOffsetWidth()
        v0 *= flingVelocity
        //???????????????velocity????????????????????????????????????offset?????????????????????
        animatorAppropriatelyBySpeed(-v0, movedTotalOffsetWhenUp)
    }

    private fun animatorAppropriatelyBySpeed(speed: Float, movedTotalOffsetWhenUp: Float) {
        if (autoHandler.hasMessages(FLING_CALCULATE_MSG)) {
            return
        }
        //S=v^2 / 2a ??????????????????????????????fling?????????????????????????????????
        var offsetDistance = speed * speed / (acceleration * 2)
        if (speed < 0) {
            offsetDistance = -offsetDistance
        }
        //fling??????????????????
        val newOffsetWhenUp = downOffset - movedTotalOffsetWhenUp
        //??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        val nearest = floor(newOffsetWhenUp + offsetDistance + 0.5f)
        //?????????????????????
        startSpeed = sqrt(abs(nearest - newOffsetWhenUp) * acceleration * 2)
        if (nearest < newOffsetWhenUp) {
            startSpeed = -startSpeed
        }
        //????????????????????????0???????????????????????????
        duration = abs(startSpeed / acceleration)
        startTime = AnimationUtils.currentAnimationTimeMillis()
        Message.obtain(autoHandler, FLING_CALCULATE_MSG, newOffsetWhenUp).sendToTarget()
        updateScrollState(SCROLL_STATE_SETTLING)
    }

    private fun calculateFling(newOffsetWhenUp: Float) {
        val elapsedTime =
            (AnimationUtils.currentAnimationTimeMillis() - startTime) / UNIT_FOR_VELOCITY_TRACKER.toFloat()
        if (elapsedTime >= duration) {
            //????????????
            stopFlingCalculate(true)
        } else {
            updateOffsetByElapsedTime(elapsedTime, newOffsetWhenUp)
            Message.obtain(autoHandler, FLING_CALCULATE_MSG, newOffsetWhenUp).sendToTarget()
        }
    }

    private fun updateOffsetByElapsedTime(elapsed: Float, newOffsetWhenUp: Float) {
        val safeElapsed = elapsed.coerceAtMost(duration)
        //??????????????????????????????????????????
        var offsetDistance: Float =
            abs(startSpeed) * safeElapsed - acceleration * safeElapsed.pow(2) / 2
        if (startSpeed < 0) {
            offsetDistance = -offsetDistance
        }
        updateOffset(newOffsetWhenUp + offsetDistance)
    }

    private fun stopFlingCalculate(adjustToNearlyIndex: Boolean) {
        if (stopAutoScrollType == STOP_AUTO_TYPE_INTERNAL) {
            startAutoScroll()
        }
        if (adjustToNearlyIndex) {
            //????????????????????????????????????????????????????????????????????????
            updateOffset(nearlyIndexBy(offset))
        }
        if (autoHandler.hasMessages(FLING_CALCULATE_MSG)) {
            autoHandler.removeMessages(FLING_CALCULATE_MSG)
        }
        updateScrollState(SCROLL_STATE_IDLE)
    }

    private fun animatorChildToTop(v: View) {
        val index = indexOfChild(v)
        val layerIndex = index shr 1
        val topLayerIndex = layerCount - 1
        var layerIndexDiffer = topLayerIndex - layerIndex
        if (index % 2 == 0) {
            //?????????????????????child??????????????????????????????
            layerIndexDiffer = -layerIndexDiffer
        }
        animatorBy(layerIndexDiffer)
    }

    private fun animatorBy(layerIndexDiffer: Int) {
        if (isAnimatorChildToTopRunning) {
            return
        }
        isAnimatorChildToTopRunning = true
        val animator = ValueAnimator.ofFloat(offset, offset + layerIndexDiffer)
        animator.setDuration(swapLayerVelocity).start()
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            updateOffset(animation.animatedValue as Float)
            updateScrollState(SCROLL_STATE_SETTLING)
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isAnimatorChildToTopRunning = false
                updateScrollState(SCROLL_STATE_IDLE)
            }

            override fun onAnimationCancel(animation: Animator) {
                isAnimatorChildToTopRunning = false
                updateScrollState(SCROLL_STATE_IDLE)
            }
        })
    }

    /**
     * direction < 0, ????????????????????????
     * direction??????0??? ?????????????????????
     * direction > 0??? ?????????????????????
     */
    private fun findChild(position: Int, direction: Int): View? {
        val topLayerIndex = childCount - 1
        if (topLayerIndex == -1) {
            return null
        }
        if (direction == 0) {
            for (i in topLayerIndex downTo 0) {
                val adapterPosition =
                    getAdapterPositionForChild(layerCount, i, currentNearlyIndex.toInt())
                if (adapterPosition == position) {
                    return getChildAt(i)
                }
            }
        } else if (direction < 0) {
            var i = topLayerIndex
            while (i > -1) {
                val adapterPosition =
                    getAdapterPositionForChild(layerCount, i, currentNearlyIndex.toInt())
                if (adapterPosition == position) {
                    return getChildAt(i)
                }
                if (i == 0) {
                    i = topLayerIndex - 1
                }
                i -= 2
            }
        } else {
            var i = topLayerIndex - 1
            while (i > -1) {
                val adapterPosition =
                    getAdapterPositionForChild(layerCount, i, currentNearlyIndex.toInt())
                if (adapterPosition == position) {
                    return getChildAt(i)
                }
                if (i == 1) {
                    i = topLayerIndex
                }
                i -= 2
            }
        }
        return null
    }

    fun forwardBy(offset: Int) {
        if (!isLayout()) {
            return
        }
        animatorBy(offset)
    }

    fun forwardTo(position: Int) {
        if (!isLayout()) {
            return
        }
        val adapterPositionForTop =
            getAdapterPositionForChild(layerCount, childCount - 1, currentNearlyIndex.toInt())
        if (position == adapterPositionForTop) {
            return
        }
        val offset = if (position > adapterPositionForTop) {
            position - adapterPositionForTop
        } else {
            adapter!!.getCount() - adapterPositionForTop + position
        }
        animatorBy(offset)
    }

    fun backwardBy(offset: Int) {
        if (!isLayout()) {
            return
        }
        animatorBy(-offset)
    }

    fun backwardTo(position: Int) {
        if (!isLayout()) {
            return
        }
        val adapterPositionForTop =
            getAdapterPositionForChild(layerCount, childCount - 1, currentNearlyIndex.toInt())
        if (position == adapterPositionForTop) {
            return
        }
        val offset = if (position < adapterPositionForTop) {
            adapterPositionForTop - position
        } else {
            adapter!!.getCount() - position + adapterPositionForTop
        }
        backwardBy(offset)
    }

    fun currentVisibleCount(): Int {
        return visibleCount
    }

    fun topPosition(): Int {
        if (!isLayout()) {
            throw IllegalAccessException("")
        }
        return getAdapterPositionForChild(layerCount, childCount - 1, currentNearlyIndex.toInt())
    }

    fun isVisible(position: Int): Boolean {
        val child = findChild(position, 0) ?: return false
        val index = indexOfChild(child)
        return index > 1
    }

    fun getIndex(position: Int, direction: Int): Int {
        val child = findChild(position, direction) ?: return -1
        return indexOfChild(child)
    }

    fun getLayerLevel(position: Int, direction: Int): Int {
        val child = findChild(position, direction) ?: return -1
        val index = indexOfChild(child)
        if (index == -1) {
            return -1
        }
        return index shr 1
    }

    fun setOnLayerChangeListener(listener: OnLayerChangeListener) {
        this.onLayerChangeListener = listener
    }

    fun setSelect(position: Int, direction: Int, animator: Boolean) {
        if (position < 0 || position >= adapter?.getCount() ?: 0) {
            return
        }
        if (!isLayout()) {
            offset = position.toFloat()
            return
        }
        if (animator) {
            //?????????????????????offset????????????????????????
            if (direction == 0) {
                //?????????????????????????????????child?????????????????????child???
                // ??????????????????????????????????????????????????????
                val child = findChild(position, direction)
                if (child != null) {
                    animatorChildToTop(child)
                } else {
                    forwardTo(position)
                }
            } else if (direction < 0) {
                backwardTo(position)
            } else {
                forwardTo(position)
            }
        } else {
            reset(true)
            offset = position.toFloat()
            currentNearlyIndex = offset
            createAndBindView()
        }
    }

    fun setAdapter(adapter: CardsBannerAdapter) {
        this.adapter?.unbindToBanner()
        this.adapter = adapter
        adapter.bindToBanner(this)
        initChildViewInner(false)
    }

    fun getAdapter(): CardsBannerAdapter? {
        return adapter
    }

    fun getCurrent(): CardsBannerViewHolder? {
        return if (isLayout()) {
            occupiedViewHolders[getChildAt(childCount - 1)]
        } else null
    }

    internal fun onDataSetChanged() {
        initChildViewInner(true)
    }

    private fun initChildViewInner(keepRecycled: Boolean) {
        reset(keepRecycled)
        if (adapter!!.getCount() == 0) {
            return
        }
        createAndBindView()
    }

    private fun createAndBindView() {
        for (i in 0 until totalCount) {
            val position = getAdapterPositionForChild(layerCount, i, currentNearlyIndex.toInt())
            if (i == 0) {
                leftPos = position
            } else if (i == 1) {
                rightPos = position
            }
            val viewHolder: CardsBannerViewHolder = onCreateViewHolder(position)
            val holdView = viewHolder.holdView
            occupiedViewHolders[holdView] = viewHolder
            addView(holdView)
            onBindViewHolder(position, viewHolder)
            if (i == totalCount - 1) {
                selectRealPosition = position
                onLayerChangeListener?.onLayerTop(position, viewHolder)
            }
        }
        idleTopLayerChild = getChildAt(childCount - 1)
    }

    fun startAutoScroll() {
        stopAutoScrollType = STOP_AUTO_TYPE_NONE
        autoHandler.removeMessages(AUTO_SCROLL_MSG)
        autoHandler.sendEmptyMessageDelayed(AUTO_SCROLL_MSG, autoScrollFrequency)
    }

    fun stopAutoScroll() {
        stopAutoScroll(STOP_AUTO_TYPE_USER)
    }

    private fun stopAutoScroll(stopAutoScrollType: Int) {
        this.stopAutoScrollType = stopAutoScrollType
        autoHandler.removeMessages(AUTO_SCROLL_MSG)
    }

    private fun resetAllAnimator() {
        if (autoHandler.hasMessages(AUTO_SCROLL_MSG)) {
            stopAutoScroll(STOP_AUTO_TYPE_INTERNAL)
            startAutoScroll()
        }
        stopFlingCalculate(true)
    }

    fun getAdapterPositionForHolder(holder: CardsBannerViewHolder): Int {
        return getAdapterPositionForChild(
            layerCount,
            indexOfChild(holder.holdView),
            currentNearlyIndex.toInt()
        )
    }

    private fun getAdapterPositionForChild(
        layerCount: Int,
        childIndex: Int,
        nearlyIndex: Int
    ): Int {
        //????????????child??????(??????view??????????????????)??????????????????????????????????????????????????????
        //???????????????5???totalCount???7???????????????child view???index???0???2???4???6??????fakePosition?????????-3???-2???-1???0?????????position????????? 2???3???4, 0
        //??????child view???index???1???3???5??????fakePosition?????????1???2???3?????????position????????? 1???2???3
        //?????????????????????????????????????????????????????????
        if (adapter?.getCount() == 0) {
            throw IllegalStateException("must set adapter first")
        }
        if (childIndex == -1) {
            return -1
        }
        var fakePosition = if (childIndex % 2 == 0) {
            0 - (layerCount - 1 - childIndex / 2)
        } else {
            layerCount - (childIndex + 1) / 2
        }
        fakePosition += nearlyIndex
        val position = if (fakePosition < 0) {
            val remain = fakePosition % adapter!!.getCount()
            if (remain == 0) 0 else adapter!!.getCount() + remain
        } else {
            fakePosition % adapter!!.getCount()
        }
        return position
    }

    private fun onCreateViewHolder(position: Int): CardsBannerViewHolder {
        val itemType = adapter!!.getItemType(position)
        var holder = recycledViewHolders.find { it.getItemViewType() == itemType }
        if (holder != null) {
            recycledViewHolders.remove(holder)
            adapter!!.onCreateViewHolder(holder, itemType)
        } else {
            holder = adapter!!.onCreateViewHolder(null, itemType)
        }
        holder.itemType = itemType
        val width = context.resources.displayMetrics.widthPixels
        val sizeDivider = 2 + (visibleCount - 3) * 0.5
        val size: Int = (width / sizeDivider).toInt()
        childWidth = size
        childHeight = size
        holder.holdView.isClickable = true
        holder.holdView.layoutParams = ViewGroup.LayoutParams(size, size)
        return holder
    }

    private fun onBindViewHolder(position: Int, holder: CardsBannerViewHolder) {
        holder.ownerCardsBannerView = this
        adapter!!.onBindViewHolder(position, holder)
    }

    private fun onUnbindViewHolder(position: Int, holder: CardsBannerViewHolder) {
        holder.ownerCardsBannerView = null
        recycledViewHolders.add(holder)
        adapter!!.onViewRecycled(position, holder)
    }
}