package com.photounifier

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/** Shared, stateless manual brightness/contrast adjustment. */
internal object BitmapAdjustments {
    fun apply(source: Bitmap, brightness: Int, contrast: Float): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        try {
            val scale = contrast.coerceIn(0.5f, 2.0f)
            val offset = brightness.toFloat()
            val matrix = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, offset,
                0f, scale, 0f, 0f, offset,
                0f, 0f, scale, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            ))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }
            Canvas(output).drawBitmap(source, 0f, 0f, paint)
            return output
        } catch (error: Throwable) {
            // If rendering fails after allocation (for example under memory pressure),
            // release the native bitmap before propagating the original failure.
            if (!output.isRecycled) output.recycle()
            throw error
        }
    }
}
