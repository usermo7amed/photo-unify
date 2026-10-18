/*
 * FairScan segmentation model integration.
 *
 * The model is distributed under GNU GPL v3.0. See THIRD_PARTY_NOTICES.md
 * and the LICENSE.txt file packaged with the model asset.
 */
package com.photounifier

import android.content.Context
import android.graphics.Bitmap
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Runs FairScan's on-device segmentation model and converts its probability map
 * into a conservative page quadrilateral. Returns null on any model/runtime
 * failure so DocumentCrop can use its existing OpenCV detector.
 */
internal object FairScanDocumentSegmenter : Closeable {
    private const val MODEL_ASSET = "fairscan-segmentation-model.tflite"
    private const val INPUT_SIZE = 256
    private val lock = Any()
    @Volatile private var interpreter: Interpreter? = null

    fun detect(context: Context, bitmap: Bitmap): List<CropPoint>? = synchronized(lock) {
        if (bitmap.width < 32 || bitmap.height < 32) return null
        val model = try {
            interpreter ?: Interpreter(
                context.applicationContext.assets.open(MODEL_ASSET).use { input ->
                    val bytes = input.readBytes()
                    ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                        put(bytes)
                        rewind()
                    }
                },
                Interpreter.Options().setNumThreads(2)
            ).also { interpreter = it }
        } catch (_: Exception) {
            return null
        }

        val scaled = try {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        } catch (_: Exception) {
            return null
        }
        val inputBuffer = ByteBuffer.allocateDirect(
            1 * INPUT_SIZE * INPUT_SIZE * 3 * 4
        ).order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        val output = Array(1) {
            Array(INPUT_SIZE) { Array(INPUT_SIZE) { FloatArray(1) } }
        }
        try {
            scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            for (pixel in pixels) {
                val red = ((pixel shr 16) and 0xff) / 255f
                val green = ((pixel shr 8) and 0xff) / 255f
                val blue = (pixel and 0xff) / 255f
                // Same ImageNet normalization used by the FairScan training pipeline.
                inputBuffer.putFloat((red - 0.485f) / 0.229f)
                inputBuffer.putFloat((green - 0.456f) / 0.224f)
                inputBuffer.putFloat((blue - 0.406f) / 0.225f)
            }
            inputBuffer.rewind()
            model.run(inputBuffer, output)
        } catch (_: Exception) {
            return null
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        }

        val probability = Mat(INPUT_SIZE, INPUT_SIZE, CvType.CV_32FC1)
        val probabilityValues = FloatArray(INPUT_SIZE * INPUT_SIZE)
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val logit = output[0][y][x][0]
                // The exported network emits logits; sigmoid makes a probability map.
                probabilityValues[y * INPUT_SIZE + x] =
                    (1.0 / (1.0 + kotlin.math.exp(-logit.toDouble()))).toFloat()
            }
        }
        probability.put(0, 0, probabilityValues)
        try {
            return bestQuadrilateral(probability, bitmap.width, bitmap.height)
        } finally {
            probability.release()
        }
    }

    private fun bestQuadrilateral(probability: Mat, sourceWidth: Int, sourceHeight: Int): List<CropPoint>? {
        val smooth = Mat()
        Imgproc.GaussianBlur(probability, smooth, Size(3.0, 3.0), 0.0)
        var best: List<CropPoint>? = null
        var bestScore = 0.0
        try {
            // The higher thresholds avoid expanding the page into shadows/background.
            for (threshold in doubleArrayOf(0.50, 0.65, 0.75, 0.85, 0.90)) {
                val binary = Mat()
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
                val hierarchy = Mat()
                val contours = ArrayList<MatOfPoint>()
                try {
                    Imgproc.threshold(smooth, binary, threshold, 1.0, Imgproc.THRESH_BINARY)
                    // Convert to an 8-bit mask before morphology/contour extraction.
                    binary.convertTo(binary, CvType.CV_8UC1, 255.0)
                    Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel)
                    Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                    val imageArea = binary.cols().toDouble() * binary.rows()
                    for (contour in contours.sortedByDescending { abs(Imgproc.contourArea(it)) }.take(8)) {
                        val contourArea = abs(Imgproc.contourArea(contour))
                        val fraction = contourArea / imageArea
                        if (fraction !in 0.045..0.985) continue
                        val curve = MatOfPoint2f(*contour.toArray())
                        try {
                            val perimeter = Imgproc.arcLength(curve, true)
                            for (factor in doubleArrayOf(0.008, 0.015, 0.025, 0.04)) {
                                val approximation = MatOfPoint2f()
                                try {
                                    Imgproc.approxPolyDP(curve, approximation, perimeter * factor, true)
                                    val raw = approximation.toArray()
                                    if (raw.size != 4 || !isConvex(raw)) continue
                                    val ordered = orderCorners(raw)
                                    val analysisPoints = ordered.map {
                                        CropPoint(it.x.toFloat(), it.y.toFloat())
                                    }
                                    if (!DocumentCrop.isPlausibleDetection(
                                            analysisPoints, INPUT_SIZE, INPUT_SIZE
                                        )
                                    ) continue
                                    val score = fraction * meanProbability(smooth, ordered)
                                    if (score > bestScore) {
                                        bestScore = score
                                        best = analysisPoints.map {
                                            CropPoint(
                                                it.x * (sourceWidth - 1).toFloat() / (INPUT_SIZE - 1),
                                                it.y * (sourceHeight - 1).toFloat() / (INPUT_SIZE - 1)
                                            )
                                        }
                                    }
                                } finally {
                                    approximation.release()
                                }
                            }
                        } finally {
                            curve.release()
                        }
                    }
                } finally {
                    contours.forEach { it.release() }
                    binary.release()
                    kernel.release()
                    hierarchy.release()
                }
            }
            return best?.takeIf { bestScore >= 0.045 }?.takeIf {
                DocumentCrop.isPlausibleDetection(it, sourceWidth, sourceHeight)
            }
        } finally {
            smooth.release()
        }
    }

    private fun meanProbability(probability: Mat, corners: Array<Point>): Double {
        // Sampling the four edges checks that the predicted page mask supports its boundary.
        var sum = 0.0
        var count = 0
        for (side in corners.indices) {
            val a = corners[side]
            val b = corners[(side + 1) % corners.size]
            val steps = max(8, (kotlin.math.hypot(b.x - a.x, b.y - a.y) / 4.0).roundToInt())
            for (i in 0..steps) {
                val t = i.toDouble() / steps
                val x = (a.x + (b.x - a.x) * t).roundToInt().coerceIn(0, probability.cols() - 1)
                val y = (a.y + (b.y - a.y) * t).roundToInt().coerceIn(0, probability.rows() - 1)
                sum += probability.get(y, x)[0]
                count++
            }
        }
        return if (count == 0) 0.0 else sum / count
    }

    private fun orderCorners(points: Array<Point>): Array<Point> {
        val centerX = points.map { it.x }.average()
        val centerY = points.map { it.y }.average()
        val around = points.sortedBy { kotlin.math.atan2(it.y - centerY, it.x - centerX) }
        val start = around.indices.minByOrNull { i -> around[i].x + around[i].y } ?: 0
        val rotated = Array(4) { around[(start + it) % 4] }
        // Enforce TL, TR, BR, BL order for the perspective transform contract.
        return if ((rotated[1].x - rotated[0].x) * (rotated[3].y - rotated[0].y) -
            (rotated[1].y - rotated[0].y) * (rotated[3].x - rotated[0].x) >= 0
        ) rotated else arrayOf(rotated[0], rotated[3], rotated[2], rotated[1])
    }

    private fun isConvex(points: Array<Point>): Boolean {
        if (points.size != 4) return false
        val signs = points.indices.map { i ->
            val a = points[i]
            val b = points[(i + 1) % 4]
            val c = points[(i + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        return signs.all { it > 0.0 } || signs.all { it < 0.0 }
    }

    override fun close() = synchronized(lock) {
        interpreter?.close()
        interpreter = null
    }
}
