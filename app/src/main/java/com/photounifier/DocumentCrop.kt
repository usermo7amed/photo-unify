package com.photounifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Coordinates are in bitmap pixels, ordered top-left, top-right, bottom-right, bottom-left. */
data class CropPoint(val x: Float, val y: Float)

/**
 * A compact, memory-safe edit record for one document page.
 * normalizedCorners are in the 0..1 range relative to the rotated source image.
 */
data class DocumentPageEdit(
    val rotationDegrees: Int = 0,
    val normalizedCorners: List<CropPoint>? = null
)

internal fun rotatePageBitmap(source: Bitmap, degrees: Int): Bitmap {
    val normalized = ((degrees % 360) + 360) % 360
    if (normalized == 0) return source
    val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

internal fun normalizeCropPoints(points: List<CropPoint>, width: Int, height: Int): List<CropPoint> {
    val maxX = (width - 1).coerceAtLeast(1).toFloat()
    val maxY = (height - 1).coerceAtLeast(1).toFloat()
    return points.map {
        CropPoint((it.x / maxX).coerceIn(0f, 1f), (it.y / maxY).coerceIn(0f, 1f))
    }
}

internal fun mapNormalizedCropPoints(points: List<CropPoint>, width: Int, height: Int): List<CropPoint> {
    val maxX = (width - 1).coerceAtLeast(1).toFloat()
    val maxY = (height - 1).coerceAtLeast(1).toFloat()
    return points.map {
        CropPoint(it.x.coerceIn(0f, 1f) * maxX, it.y.coerceIn(0f, 1f) * maxY)
    }
}

object DocumentCrop {
    private const val DETECTION_MAX_SIDE = 1280

    /**
     * Detects a document using several complementary edge/threshold passes.
     * A single Canny pass often misses pale paper, shadows, or broken edges.
     * Perspective correction still uses four outer corners; internal contour
     * points are evidence for finding those corners, not additional warp anchors.
     */
    fun detect(bitmap: Bitmap, context: Context? = null): List<CropPoint>? {
        if (bitmap.width < 32 || bitmap.height < 32) return null

        // Prefer FairScan's learned document mask. Keep the existing multi-pass
        // OpenCV detector as a graceful fallback if inference fails or is uncertain.
        if (context != null) {
            val learnedDetection = try {
                FairScanDocumentSegmenter.detect(context.applicationContext, bitmap)
            } catch (_: Exception) {
                null
            }
            if (learnedDetection != null &&
                isPlausibleDetection(learnedDetection, bitmap.width, bitmap.height)
            ) return learnedDetection
        }
        val rgba = Mat()
        val analysis = Mat()
        val gray = Mat()
        val blurred = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        val work = Mat()
        val supportEdges = Mat()
        val contrastEnhanced = Mat()
        val contrastEdges = Mat()
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val supportKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        try {
            Utils.bitmapToMat(bitmap, rgba)
            // Find page geometry on a bounded-size copy, then map its corners back to
            // the original bitmap. Cropping still uses the original source resolution.
            val analysisScale = min(
                1.0,
                DETECTION_MAX_SIDE.toDouble() / max(bitmap.width, bitmap.height).toDouble()
            )
            val analysisWidth = (bitmap.width.toDouble() * analysisScale).roundToInt().coerceAtLeast(1)
            val analysisHeight = (bitmap.height.toDouble() * analysisScale).roundToInt().coerceAtLeast(1)
            if (analysisWidth != bitmap.width || analysisHeight != bitmap.height) {
                Imgproc.resize(
                    rgba, analysis,
                    Size(analysisWidth.toDouble(), analysisHeight.toDouble()),
                    0.0, 0.0, Imgproc.INTER_AREA
                )
            } else {
                rgba.copyTo(analysis)
            }
            rgba.release()
            if (analysis.channels() == 4) Imgproc.cvtColor(analysis, gray, Imgproc.COLOR_RGBA2GRAY)
            else Imgproc.cvtColor(analysis, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            // Preserve ordinary edges, then add locally contrast-enhanced evidence.
            // CLAHE helps when white paper and a pale/uneven background have similar
            // average brightness without changing the source bitmap or its colors.
            clahe.apply(blurred, contrastEnhanced)
            Imgproc.Canny(blurred, supportEdges, 25.0, 85.0)
            Imgproc.Canny(contrastEnhanced, contrastEdges, 15.0, 55.0)
            Core.bitwise_or(supportEdges, contrastEdges, supportEdges)
            Imgproc.morphologyEx(supportEdges, supportEdges, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.dilate(supportEdges, supportEdges, supportKernel)
            val supportPixels = ByteArray(
                (supportEdges.total() * supportEdges.channels()).toInt().coerceAtLeast(0)
            )
            supportEdges.get(0, 0, supportPixels)

            val imageArea = analysis.cols().toDouble() * analysis.rows()
            var best: Array<Point>? = null
            var bestScore = 0.0

            // Try both continuous edges and adaptive thresholding. This helps
            // when a page edge is weak or partially hidden by a cast shadow.
            // Separate passes allow both clean edges and low-contrast/unevenly lit
            // page boundaries to contribute candidates. All operate on the small
            // analysis image, never on the full-resolution source.
            val passes = 5
            for (pass in 0 until passes) {
                work.release()
                when (pass) {
                    0 -> {
                        Imgproc.Canny(blurred, work, 25.0, 85.0)
                        Imgproc.morphologyEx(work, work, Imgproc.MORPH_CLOSE, kernel)
                    }
                    1 -> {
                        Imgproc.Canny(blurred, work, 45.0, 135.0)
                        Imgproc.dilate(work, work, kernel)
                        Imgproc.morphologyEx(work, work, Imgproc.MORPH_CLOSE, kernel)
                    }
                    2 -> {
                        Imgproc.adaptiveThreshold(
                            blurred, work, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                            Imgproc.THRESH_BINARY, 31, 9.0
                        )
                        Imgproc.morphologyEx(work, work, Imgproc.MORPH_CLOSE, kernel)
                    }
                    3 -> {
                        Imgproc.Canny(contrastEnhanced, work, 15.0, 55.0)
                        Imgproc.morphologyEx(work, work, Imgproc.MORPH_CLOSE, kernel)
                    }
                    else -> {
                        Imgproc.adaptiveThreshold(
                            contrastEnhanced, work, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                            Imgproc.THRESH_BINARY_INV, 31, 9.0
                        )
                        Imgproc.morphologyEx(work, work, Imgproc.MORPH_CLOSE, kernel)
                    }
                }
                contours.clear()
                Imgproc.findContours(work, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
                for (contour in contours.sortedByDescending { abs(Imgproc.contourArea(it)) }.take(100)) {
                    val area = abs(Imgproc.contourArea(contour))
                    val fraction = area / imageArea
                    if (fraction !in 0.045..0.985) continue
                    val curve = MatOfPoint2f(*contour.toArray())
                    try {
                        val perimeter = Imgproc.arcLength(curve, true)
                        for (factor in doubleArrayOf(0.008, 0.012, 0.018, 0.025, 0.035, 0.045)) {
                            val approx = MatOfPoint2f()
                            try {
                                Imgproc.approxPolyDP(curve, approx, perimeter * factor, true)
                                val points = approx.toArray()
                                if (points.size != 4 || !isConvex(points)) continue
                                val ordered = order(points)
                                val candidateFraction = abs(Imgproc.contourArea(approx)) / imageArea
                                if (candidateFraction !in 0.045..0.985) continue

                                val candidatePoints = ordered.map {
                                    CropPoint(it.x.toFloat(), it.y.toFloat())
                                }
                                if (!isPlausibleDetection(
                                        candidatePoints, analysis.cols(), analysis.rows()
                                    )
                                ) continue

                                // A candidate must have real edge evidence on its four sides.
                                // A low minimum keeps faint paper boundaries eligible.
                                val sideSupports = edgeSupportBySide(
                                    ordered, analysis.cols(), analysis.rows(), supportPixels
                                )
                                val averageSideSupport = sideSupports.average()
                                val weakestSideSupport = sideSupports.minOrNull() ?: 0.0
                                // Do not accept a quadrilateral whose apparent sides are
                                // supported only on one or two edges. Keep a softer threshold
                                // for broken edges caused by shadows or partial occlusion.
                                if (!hasReliableSideSupport(sideSupports)) continue

                                val cosine = cornerCosine(ordered)
                                val centerX = ordered.map { it.x }.average()
                                val centerY = ordered.map { it.y }.average()
                                val centerPenalty = sqrt(
                                    ((centerX - analysis.cols() / 2.0) / analysis.cols()).let { it * it } +
                                    ((centerY - analysis.rows() / 2.0) / analysis.rows()).let { it * it }
                                ).coerceAtMost(0.7)

                                // Use a square-root area term so a single huge background
                                // rectangle cannot dominate smaller, better-supported pages.
                                val areaScore = sqrt(candidateFraction)
                                val balancedSideSupport =
                                    averageSideSupport * 0.70 + weakestSideSupport * 0.30
                                val score = areaScore *
                                    (1.0 - cosine * 0.55) *
                                    (1.0 - centerPenalty * 0.12) *
                                    (0.45 + balancedSideSupport * 0.55)
                                if (score > bestScore) {
                                    best = ordered
                                    bestScore = score
                                }
                            } finally { approx.release() }
                        }
                    } finally { curve.release() }
                }
                contours.forEach { it.release() }
                contours.clear()
            }
            val detected = best?.takeIf { bestScore > 0.075 }?.map {
                CropPoint(
                    it.x.toFloat().coerceIn(0f, (analysis.cols() - 1).toFloat()),
                    it.y.toFloat().coerceIn(0f, (analysis.rows() - 1).toFloat())
                )
            } ?: return null
            return scalePointsToSource(
                detected, bitmap.width, bitmap.height, analysis.cols(), analysis.rows(), context
            )
        } finally {
            contours.forEach { it.release() }
            if (!rgba.empty()) rgba.release()
            analysis.release()
            gray.release(); blurred.release(); hierarchy.release()
            supportEdges.release(); contrastEnhanced.release(); contrastEdges.release()
            clahe.clear()
            supportKernel.release(); kernel.release(); work.release()
        }
    }

    /**
     * Filters out geometrically valid but implausible auto-detection results.
     *
     * In particular, a quadrilateral that almost traces the photo border is often
     * the image frame/table rather than the paper. Such cases should fall back to
     * manual corner adjustment instead of being silently accepted as a page.
     */
    /**
     * The fallback is intentionally conservative: a strong average must not hide
     * a completely unsupported side, which is a common signature of background
     * rectangles and shadows rather than a real page.
     */
    internal fun hasReliableSideSupport(sideSupports: DoubleArray): Boolean {
        if (sideSupports.size != 4 || sideSupports.any { !it.isFinite() || it !in 0.0..1.0 }) {
            return false
        }
        val average = sideSupports.average()
        val weakest = sideSupports.minOrNull() ?: return false
        return average >= 0.18 && weakest >= 0.035 &&
            !(average < 0.26 && weakest < 0.08)
    }

    internal fun isPlausibleDetection(
        points: List<CropPoint>,
        width: Int,
        height: Int
    ): Boolean {
        if (!isValid(points, width, height)) return false

        var twiceArea = 0.0
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            twiceArea += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        val areaFraction = abs(twiceArea) * 0.5 / (width.toDouble() * height)
        if (areaFraction !in 0.045..0.985) return false

        val minimumSide = max(8.0, min(width, height) * 0.035)
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            val dx = (a.x - b.x).toDouble()
            val dy = (a.y - b.y).toDouble()
            if (sqrt(dx * dx + dy * dy) < minimumSide) return false
        }

        // If most corners hug the image edges and the candidate covers nearly
        // the whole photo, there is not enough evidence that it is the paper.
        val edgeMargin = max(3f, min(width, height) * 0.02f)
        val frameAdjacentCorners = points.count { point ->
            point.x <= edgeMargin ||
                point.x >= width - 1f - edgeMargin ||
                point.y <= edgeMargin ||
                point.y >= height - 1f - edgeMargin
        }
        if (areaFraction > 0.70 && frameAdjacentCorners >= 3) return false

        return true
    }

    internal fun scalePointsToSource(
        points: List<CropPoint>,
        sourceWidth: Int,
        sourceHeight: Int,
        analysisWidth: Int,
        analysisHeight: Int,
        context: Context? = null
    ): List<CropPoint> {
        require(sourceWidth > 0 && sourceHeight > 0 && analysisWidth > 0 && analysisHeight > 0) {
            context?.getString(R.string.error_invalid_image_dimensions) ?: "Invalid image dimensions"
        }
        // Align the first and last pixel centers so detected frame edges map exactly.
        val scaleX = (sourceWidth - 1).toFloat() / (analysisWidth - 1).coerceAtLeast(1)
        val scaleY = (sourceHeight - 1).toFloat() / (analysisHeight - 1).coerceAtLeast(1)
        return points.map { point ->
            CropPoint(
                (point.x * scaleX).coerceIn(0f, (sourceWidth - 1).toFloat()),
                (point.y * scaleY).coerceIn(0f, (sourceHeight - 1).toFloat())
            )
        }
    }

    fun fullFrame(width: Int, height: Int): List<CropPoint> = listOf(
        CropPoint(0f, 0f), CropPoint((width - 1).toFloat(), 0f),
        CropPoint((width - 1).toFloat(), (height - 1).toFloat()),
        CropPoint(0f, (height - 1).toFloat())
    )

    fun isValid(points: List<CropPoint>, width: Int, height: Int): Boolean {
        if (points.size != 4 || width < 2 || height < 2) return false
        if (points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..(width - 1).toFloat() || it.y !in 0f..(height - 1).toFloat() }) return false
        val polygon = points.map { it.x.toDouble() to it.y.toDouble() }
        var twiceArea = 0.0
        for (i in 0..3) {
            val a = polygon[i]; val b = polygon[(i + 1) % 4]
            twiceArea += a.first * b.second - b.first * a.second
        }
        if (abs(twiceArea) < width.toDouble() * height * 0.015) return false
        if (segmentsIntersect(polygon[0], polygon[1], polygon[2], polygon[3]) ||
            segmentsIntersect(polygon[1], polygon[2], polygon[3], polygon[0])) return false
        // A valid perspective quadrilateral must be strictly convex. Merely
        // rejecting intersecting opposite edges still allows concave point sets,
        // which can produce folded or unstable perspective transforms.
        val crosses = points.indices.map { i ->
            val a = points[(i + 3) % 4]
            val b = points[i]
            val c = points[(i + 1) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        if (crosses.any { abs(it) <= 1f }) return false
        return crosses.all { it > 0f } || crosses.all { it < 0f }
    }

    fun warp(
        bitmap: Bitmap,
        points: List<CropPoint>,
        maxOutputSide: Int? = null,
        context: Context? = null
    ): Bitmap {
        require(isValid(points, bitmap.width, bitmap.height)) {
            context?.getString(R.string.error_invalid_crop_corners) ?: "Invalid crop corners"
        }
        require(maxOutputSide == null || maxOutputSide > 1) {
            context?.getString(R.string.error_invalid_crop_preview_size) ?: "Invalid crop preview size"
        }

        // For interactive previews, scale the source before converting it to an OpenCV
        // Mat. This avoids allocating a full-resolution native matrix on every drag.
        // A null maxOutputSide keeps the full-resolution output path unchanged.
        val previewScale = if (maxOutputSide == null) 1.0 else
            min(1.0, maxOutputSide.toDouble() / max(bitmap.width, bitmap.height).toDouble())
        val workingWidth = (bitmap.width * previewScale).roundToInt().coerceAtLeast(2)
        val workingHeight = (bitmap.height * previewScale).roundToInt().coerceAtLeast(2)
        val workingBitmap = if (workingWidth == bitmap.width && workingHeight == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, workingWidth, workingHeight, true)
        }
        val workingPoints = if (workingBitmap === bitmap) points else {
            val scaleX = (workingWidth - 1).toFloat() / (bitmap.width - 1).coerceAtLeast(1)
            val scaleY = (workingHeight - 1).toFloat() / (bitmap.height - 1).coerceAtLeast(1)
            points.map { CropPoint(it.x * scaleX, it.y * scaleY) }
        }
        val ordered = order(workingPoints.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        var outWidth = max(distance(ordered[0], ordered[1]), distance(ordered[3], ordered[2])).roundToInt().coerceIn(2, 4096)
        var outHeight = max(distance(ordered[0], ordered[3]), distance(ordered[1], ordered[2])).roundToInt().coerceIn(2, 4096)
        if (maxOutputSide != null) {
            val outputScale = min(1.0, maxOutputSide.toDouble() / max(outWidth, outHeight).toDouble())
            outWidth = (outWidth * outputScale).roundToInt().coerceIn(2, 4096)
            outHeight = (outHeight * outputScale).roundToInt().coerceIn(2, 4096)
        }
        val source = MatOfPoint2f(*ordered)
        val target = MatOfPoint2f(
            Point(0.0, 0.0), Point((outWidth - 1).toDouble(), 0.0),
            Point((outWidth - 1).toDouble(), (outHeight - 1).toDouble()),
            Point(0.0, (outHeight - 1).toDouble())
        )
        val transform = Imgproc.getPerspectiveTransform(source, target, Core.DECOMP_LU)
        val rgba = Mat()
        val output = Mat()
        try {
            Utils.bitmapToMat(workingBitmap, rgba)
            Imgproc.warpPerspective(rgba, output, transform, Size(outWidth.toDouble(), outHeight.toDouble()), Imgproc.INTER_CUBIC)
            return Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(output, it) }
        } finally {
            source.release(); target.release(); transform.release(); rgba.release(); output.release()
            if (workingBitmap !== bitmap && !workingBitmap.isRecycled) workingBitmap.recycle()
        }
    }

    /**
     * Measures the fraction of samples along candidate sides that have nearby edge
     * pixels. Sampling with a small neighborhood tolerates shadows and imperfect
     * contour localization without requiring every edge pixel to be continuous.
     */
    /**
     * Measures edge evidence independently for each side. Keeping a per-side score
     * prevents three strong sides from making an unsupported fourth side look valid.
     * A small neighborhood tolerates mild localization errors and broken edges.
     */
    private fun edgeSupportBySide(
        points: Array<Point>,
        width: Int,
        height: Int,
        edgePixels: ByteArray
    ): DoubleArray {
        return DoubleArray(points.size) { index ->
            val start = points[index]
            val end = points[(index + 1) % points.size]
            val samples = distance(start, end).roundToInt().coerceIn(12, 160)
            var supportedSamples = 0
            var totalSamples = 0

            for (step in 0..samples) {
                val fraction = step.toDouble() / samples
                val x = (start.x + (end.x - start.x) * fraction).roundToInt()
                val y = (start.y + (end.y - start.y) * fraction).roundToInt()
                var found = false

                for (dy in -2..2) {
                    val sampleY = y + dy
                    if (sampleY !in 0 until height) continue
                    for (dx in -2..2) {
                        val sampleX = x + dx
                        if (sampleX !in 0 until width) continue
                        if (edgePixels[sampleY * width + sampleX].toInt() != 0) {
                            found = true
                            break
                        }
                    }
                    if (found) break
                }

                if (found) supportedSamples++
                totalSamples++
            }

            if (totalSamples == 0) 0.0
            else supportedSamples.toDouble() / totalSamples
        }
    }

    private fun isConvex(p: Array<Point>): Boolean {
        val contour = MatOfPoint(*p)
        return try { Imgproc.isContourConvex(contour) } finally { contour.release() }
    }
    private fun order(points: Array<Point>): Array<Point> {
        val cx = points.map { it.x }.average(); val cy = points.map { it.y }.average()
        val sorted = points.sortedBy { atan2(it.y - cy, it.x - cx) }
        val start = sorted.indices.minByOrNull { sorted[it].x + sorted[it].y } ?: 0
        val rotated = Array(4) { sorted[(start + it) % 4] }
        // Ensure TL -> TR -> BR -> BL in image coordinates.
        if (rotated[1].x < rotated[3].x) return arrayOf(rotated[0], rotated[3], rotated[2], rotated[1])
        return rotated
    }
    private fun cornerCosine(p: Array<Point>): Double {
        var maxCos = 0.0
        for (i in 0..3) {
            val a = p[(i + 3) % 4]; val b = p[i]; val c = p[(i + 1) % 4]
            val ux = a.x - b.x; val uy = a.y - b.y; val vx = c.x - b.x; val vy = c.y - b.y
            val denom = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
            if (denom < 1e-6) return 1.0
            maxCos = max(maxCos, abs((ux * vx + uy * vy) / denom))
        }
        return maxCos
    }
    private fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
    private fun segmentsIntersect(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>, d: Pair<Double, Double>): Boolean {
        fun orient(p: Pair<Double, Double>, q: Pair<Double, Double>, r: Pair<Double, Double>) =
            (q.first - p.first) * (r.second - p.second) - (q.second - p.second) * (r.first - p.first)
        val o1 = orient(a, b, c); val o2 = orient(a, b, d); val o3 = orient(c, d, a); val o4 = orient(c, d, b)
        return (o1 > 0) != (o2 > 0) && (o3 > 0) != (o4 > 0)
    }
}
