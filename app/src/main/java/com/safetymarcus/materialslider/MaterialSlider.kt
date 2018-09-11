package com.safetymarcus.materialslider

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.support.v4.content.res.ResourcesCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.widget.SeekBar

/**
 * @author Marcus Hooper
 *
 * A custom seekbar implementation geared towards smoothly moving between discrete value
 * Similar to https://material.io/design/components/sliders.html#discrete-slider
 *
 * This is done purely as a wrapper over Seekbar with some minor changes to the onDraw function.
 */
class MaterialSlider(context: Context?, attrs: AttributeSet?) : SeekBar(context, attrs)
{
	private val tickBeforePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = R.color.colorAccent.getColour(context)
		style = Paint.Style.FILL
	}

	private val tickAfterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = R.color.colorPrimaryDark.getColour(context)
		style = Paint.Style.FILL
	}

	//Used to make sliding appear smooth as the default OS behaviour is to jump between discrete values
	private var _max = 99
	private var _min = 0

	//Used for animation purposes
	private var progressStart = 0

	/**
	 * The number of sections to be shown.
	 * e.g. with 3 sections, the values will be 0, 1, 2, 3
	 */
	private var sections = 3
	private var increments = ArrayList<Int>().apply {
		for (i in _min.._max step _max / sections)
			add(i)
	}

	private var blockProgressChange = false

	private var _progress = 0
	//The increment nearest to the current progress
	private val progressIncrement get() = getIncrementClosestTo(_progress)

	/**
	 * Set to receive updates whenever progress is changed. This will only return values in the form of the
	 * pre-set increments, and only when the thumb drawable reaches one of them
	 */
	var progressChangeListener: ((Int) -> Unit)? = null

	init
	{
		max = _max
		min = _min

		setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener
		{
			override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean)
			{
				if(fromUser) _progress = value
				if(blockProgressChange) progress = progressStart
			}

			override fun onStartTrackingTouch(seekBar: SeekBar?)
			{
				progressStart = progress
			}

			override fun onStopTrackingTouch(seekBar: SeekBar?)
			{
				blockProgressChange = false
				val end = increments[progressIncrement]
				animateBetweenValues(progress, end, 100)
			}
		})

		context?.obtainStyledAttributes(attrs, R.styleable.MaterialSlider)?.let {
			val thumbColor = it.getColor(R.styleable.MaterialSlider_thumbColour, R.color.colorPrimaryDark.getColour(context))
			thumb.setColorFilter(thumbColor, PorterDuff.Mode.SRC_IN)
			tickBeforePaint.color = it.getColor(R.styleable.MaterialSlider_tickSelectedColour, R.color.colorAccent.getColour(context))
			tickAfterPaint.color = it.getColor(R.styleable.MaterialSlider_tickColour, R.color.colorPrimaryDark.getColour(context))
			sections = it.getInt(R.styleable.MaterialSlider_sections, 3)
			it.recycle()
		}
	}

	override fun onTouchEvent(event: MotionEvent?): Boolean
	{
		return when(event?.action ?: return false) {
			MotionEvent.ACTION_DOWN -> {
				blockProgressChange = true
				super.onTouchEvent(event)
			}
			MotionEvent.ACTION_MOVE -> {
				blockProgressChange = false
				super.onTouchEvent(event)
			}

			MotionEvent.ACTION_UP -> {
				super.onTouchEvent(event)
			}
			else -> super.onTouchEvent(event)
		}
	}

	override fun onDraw(canvas: Canvas?)
	{
		super.onDraw(canvas)
		//removing padding to get width of the progress bar
		val increment = ((width - paddingLeft - paddingRight) / sections).toFloat()
		for (i in 0..sections)
		{
			val position = (increment * i) + paddingLeft //adding padding left because 0 is actually 0 + padding left
			if(thumb.bounds.right in position - 10 .. position + 10) continue
			canvas?.drawCircle(position, height.toFloat() / 2, 1.convertToPixels(context).toFloat(),
					if(position < thumb.bounds.centerX()) tickBeforePaint else tickAfterPaint)
		}
	}

	/**
	 * Animates the thumb drawable from where it is to the desired end position by updating the progress.
	 * Once complete, this will notify the progress listener of the change if there is one
	 */
	private fun animateBetweenValues(start: Int, end: Int, length: Long)
	{
		ValueAnimator.ofInt(start, end).apply {
			duration = length
			addUpdateListener {
				progress = it.animatedValue as Int
				interpolator = DecelerateInterpolator()
				if (progress == end)
					progressChangeListener?.invoke(progressIncrement)
			}
		}.start()
	}

	/**
	 * Gets the increment from the list of increments that is closest to the passed in value
	 * This is also used in order to return the increment that is currently selected
	 */
	private fun getIncrementClosestTo(value: Int): Int
	{
		var nearestDistance = Math.abs(increments[0] - value)
		var nearest = 0
		for (i in 1 until increments.size)
		{
			val currentDistance = Math.abs(increments[i] - value)
			if (currentDistance < nearestDistance)
			{
				nearest = i
				nearestDistance = currentDistance
			}
		}
		return nearest
	}

	override fun setMax(max: Int)
	{
		super.setMax(max)
		_max = 100 * max
	}

	override fun setMin(min: Int)
	{
		super.setMin(min)
		_min = min
	}

	/**
	 * Sets the current progress to the increment passed in
	 */
	fun setIncrement(increment: Int)
	{
		animateBetweenValues(progress, increments[increment], 200)
	}
}

fun Int.getColour(context: Context?) = context?.let { ResourcesCompat.getColor(it.resources, this, null) } ?: 0
fun Int.convertToPixels(context: Context?) = context?.let { resContext ->
	Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resContext.resources.displayMetrics))
			.takeIf { it > 1 } ?: 1
} ?: 0