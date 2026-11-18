package dev.lite.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Рисует либо иконку приложения, либо превью папки (до 4 мини-иконок).
 * Никаких размытий и теней: один drawBitmap на иконку.
 */
class GlyphView(context: Context) : View(context) {
    private var single: Bitmap? = null
    private var minis: List<Bitmap> = emptyList()
    private var isFolder = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun bindApp(b: Bitmap) {
        single = b
        minis = emptyList()
        isFolder = false
        invalidate()
    }

    fun bindFolder(list: List<Bitmap>) {
        single = null
        minis = list.take(4)
        isFolder = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (!isFolder) {
            val b = single ?: return
            rect.set(0f, 0f, w, h)
            canvas.drawBitmap(b, null, rect, bmpPaint)
            return
        }
        val r = w * 0.24f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, bgPaint)
        val pad = w * 0.12f
        val cell = (w - pad * 3f) / 2f
        minis.forEachIndexed { i, b ->
            val x = pad + (i % 2) * (cell + pad)
            val y = pad + (i / 2) * (cell + pad)
            rect.set(x, y, x + cell, y + cell)
            canvas.drawBitmap(b, null, rect, bmpPaint)
        }
    }
}
