package br.dev.monitorremoto.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Prévia local somente; zoom não consulta a câmera nem transmite outros quadros. */
internal class PhotoZoomView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var image: Bitmap? = null
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (image == null) return false
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 5f)
            limitPan()
            invalidate()
            return true
        }
    })
    private val doubleTapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (zoom > 1.1f) resetZoom() else { zoom = 2f; limitPan(); invalidate() }
            return true
        }
    })

    fun showPhoto(bitmap: Bitmap?) {
        val previous = image
        image = bitmap
        zoom = 1f; offsetX = 0f; offsetY = 0f
        invalidate()
        if (previous !== bitmap) previous?.recycle()
    }

    fun hasPhoto(): Boolean = image != null
    fun resetZoom() { zoom = 1f; offsetX = 0f; offsetY = 0f; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val photo = image ?: return
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val fit = min(w / photo.width, h / photo.height)
        val drawW = photo.width * fit * zoom
        val drawH = photo.height * fit * zoom
        canvas.drawBitmap(photo, null, RectF((w - drawW) / 2 + offsetX, (h - drawH) / 2 + offsetY,
            (w + drawW) / 2 + offsetX, (h + drawH) / 2 + offsetY), paint)
    }

    private fun limitPan() {
        val photo = image ?: return
        if (width <= 0 || height <= 0) { offsetX = 0f; offsetY = 0f; return }
        val fit = min(width.toFloat() / photo.width, height.toFloat() / photo.height)
        val maxX = max(0f, (photo.width * fit * zoom - width) / 2f)
        val maxY = max(0f, (photo.height * fit * zoom - height) / 2f)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (image == null) return false
        doubleTapDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y }
                MotionEvent.ACTION_MOVE -> {
                    if (zoom > 1f) {
                        offsetX += event.x - lastX
                        offsetY += event.y - lastY
                        limitPan(); invalidate()
                    }
                    lastX = event.x; lastY = event.y
                }
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
