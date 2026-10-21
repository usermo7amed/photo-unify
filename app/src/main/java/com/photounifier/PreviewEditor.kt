package com.photounifier

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.abs
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun BeforeAfterPreview(
    source: android.graphics.Bitmap?,
    enhanced: android.graphics.Bitmap?,
    modifier: Modifier = Modifier
) {
    var revealTarget by remember(source, enhanced) { mutableStateOf(0.5f) }
    var isHoldingOriginal by remember { mutableStateOf(false) }
    var zoom by remember(source, enhanced) { mutableStateOf(1f) }
    var pan by remember(source, enhanced) { mutableStateOf(Offset.Zero) }

    val safeSource = source?.takeUnless { it.isRecycled }
    val safeEnhanced = enhanced?.takeUnless { it.isRecycled }
    val dividerColor = MaterialTheme.colorScheme.primary

    // If holding original, temporarily force reveal to 1f (100% original)
    val effectiveReveal = if (isHoldingOriginal) 1f else revealTarget

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 120.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF14171C))
                .pointerInput(safeSource, safeEnhanced) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                        pan = if (zoom <= 1f) Offset.Zero else pan + panChange
                    }
                }
        ) {
            val componentWidth = constraints.maxWidth.toFloat()

            if (safeSource != null) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val after = safeEnhanced ?: safeSource
                    val fit = minOf(
                        size.width / safeSource.width.toFloat(),
                        size.height / safeSource.height.toFloat()
                    )
                    fun drawFitted(bitmap: android.graphics.Bitmap) {
                        val drawWidth = (bitmap.width * fit * zoom).roundToInt().coerceAtLeast(1)
                        val drawHeight = (bitmap.height * fit * zoom).roundToInt().coerceAtLeast(1)
                        val left = ((size.width - drawWidth) / 2f + pan.x).roundToInt()
                        val top = ((size.height - drawHeight) / 2f + pan.y).roundToInt()
                        drawImage(
                            image = bitmap.asImageBitmap(),
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(left, top),
                            dstSize = IntSize(drawWidth, drawHeight),
                            filterQuality = FilterQuality.High
                        )
                    }

                    // Draw after (enhanced) image first
                    drawFitted(after)

                    // In RTL, "قبل" (Original) is on the right side, so reveal the original from the right edge
                    if (effectiveReveal > 0f) {
                        clipRect(
                            left = size.width * (1f - effectiveReveal),
                            top = 0f,
                            right = size.width,
                            bottom = size.height
                        ) {
                            drawFitted(safeSource)
                        }
                    }

                    // Draw separator line and handle unless viewing 100% of one side
                    if (!isHoldingOriginal && effectiveReveal in 0.005f..0.995f) {
                        val dividerX = size.width * (1f - effectiveReveal)
                        // Glowing divider line
                        drawLine(
                            Color.White.copy(alpha = 0.95f),
                            Offset(dividerX, 0f),
                            Offset(dividerX, size.height),
                            strokeWidth = 3.dp.toPx()
                        )
                        // Outer circular badge
                        drawCircle(
                            Color.White,
                            radius = 18.dp.toPx(),
                            center = Offset(dividerX, size.height / 2f)
                        )
                        // Inner colored pill with accent
                        drawCircle(
                            dividerColor,
                            radius = 14.dp.toPx(),
                            center = Offset(dividerX, size.height / 2f)
                        )
                        // Center dot
                        drawCircle(
                            Color.White,
                            radius = 4.dp.toPx(),
                            center = Offset(dividerX, size.height / 2f)
                        )
                    }
                }

                // Interactive transparent draggable overlay directly over the divider
                if (!isHoldingOriginal && componentWidth > 0f) {
                    val dividerXOffset = (componentWidth * (1f - effectiveReveal)) - 28f
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(dividerXOffset.roundToInt(), 0) }
                            .fillMaxHeight()
                            .width(56.dp)
                            .pointerInput(componentWidth) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        val deltaFraction: Float = dragAmount / componentWidth
                                        val currentVal: Float = revealTarget
                                        revealTarget = (currentVal - deltaFraction).coerceIn(0.01f, 0.99f)
                                    }
                                )
                            }
                    )
                }

                // Floating glass badges for "قبل" and "بعد" inside the image preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = stringResource(R.string.comparison_after),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = stringResource(R.string.comparison_before),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom floating pill: status or hold hint
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (safeEnhanced == null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.preview_waiting_result),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        // Quick hold-to-compare pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isHoldingOriginal) dividerColor else Color.Black.copy(alpha = 0.60f),
                            modifier = Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val anyDown = event.changes.any { it.pressed }
                                        isHoldingOriginal = anyDown
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = if (isHoldingOriginal) stringResource(R.string.preview_original_now) else stringResource(R.string.preview_hold_original),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (zoom > 1f) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.60f)
                        ) {
                            Text(
                                text = stringResource(R.string.zoom_percent, (zoom * 100).roundToInt()),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.choose_image_compare),
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Compact control strip keeps the document canvas as tall as the available workspace.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { revealTarget = 1f },
                enabled = safeSource != null && safeEnhanced != null,
                modifier = Modifier.weight(1f).height(34.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.original), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { revealTarget = 0.5f },
                enabled = safeSource != null && safeEnhanced != null,
                modifier = Modifier.weight(1f).height(34.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "50/50",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (abs(revealTarget - 0.5f) < 0.05f) FontWeight.Bold else FontWeight.Normal
                )
            }
            OutlinedButton(
                onClick = { revealTarget = 0f },
                enabled = safeSource != null && safeEnhanced != null,
                modifier = Modifier.weight(1f).height(34.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.enhanced), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { zoom = (zoom / 1.25f).coerceAtLeast(1f); if (zoom == 1f) pan = Offset.Zero },
                enabled = zoom > 1f,
                modifier = Modifier.weight(0.7f).height(34.dp),
                shape = RoundedCornerShape(10.dp)
            ) { Text("−", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = { zoom = (zoom * 1.25f).coerceAtMost(5f) },
                enabled = safeSource != null,
                modifier = Modifier.weight(0.7f).height(34.dp),
                shape = RoundedCornerShape(10.dp)
            ) { Text("+", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = { zoom = 1f; pan = Offset.Zero; revealTarget = 0.5f },
                modifier = Modifier.weight(1f).height(34.dp),
                shape = RoundedCornerShape(10.dp)
            ) { Text(stringResource(R.string.action_reset_simple), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
internal fun CropCornerEditor(
    bitmap: android.graphics.Bitmap?,
    corners: List<CropPoint>,
    onMoveCorner: (Int, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bitmap == null || bitmap.isRecycled || corners.size != 4) return
    val latestCorners by rememberUpdatedState(corners)
    var activeCorner by remember(bitmap) { mutableStateOf(-1) }
    var magnifierPosition by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val cropAccent = MaterialTheme.colorScheme.primary
    val selectedCornerColor = MaterialTheme.colorScheme.tertiary
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF16191D))
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.corner_points_description),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        Canvas(modifier = Modifier.fillMaxSize().pointerInput(bitmap) {
            var dragPosition = Offset.Zero
            detectDragGestures(
                onDragStart = { position ->
                    val scale = minOf(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
                    val left = (size.width - bitmap.width * scale) / 2f
                    val top = (size.height - bitmap.height * scale) / 2f
                    val screenPoints = latestCorners.map { Offset(left + it.x * scale, top + it.y * scale) }
                    activeCorner = screenPoints.indices.minByOrNull { (screenPoints[it] - position).getDistance() } ?: -1
                    // A generous touch radius makes small corners easier to grab on phones.
                    if (activeCorner >= 0 && (screenPoints[activeCorner] - position).getDistance() > 76.dp.toPx()) activeCorner = -1
                    dragPosition = position
                    magnifierPosition = position
                },
                onDrag = { change, amount ->
                    if (activeCorner >= 0) {
                        change.consume()
                        dragPosition += amount
                        magnifierPosition = dragPosition
                        val scale = minOf(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
                        val left = (size.width - bitmap.width * scale) / 2f
                        val top = (size.height - bitmap.height * scale) / 2f
                        val x = ((dragPosition.x - left) / scale).coerceIn(0f, (bitmap.width - 1).toFloat())
                        val y = ((dragPosition.y - top) / scale).coerceIn(0f, (bitmap.height - 1).toFloat())
                        onMoveCorner(activeCorner, x, y)
                    }
                },
                onDragEnd = { activeCorner = -1 },
                onDragCancel = { activeCorner = -1 }
            )
        }) {
            val scale = minOf(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
            val left = (size.width - bitmap.width * scale) / 2f
            val top = (size.height - bitmap.height * scale) / 2f
            val screenPoints = corners.map { Offset(left + it.x * scale, top + it.y * scale) }

            // 1) Subtle tint inside document crop quad
            val quadPath = Path().apply {
                moveTo(screenPoints[0].x, screenPoints[0].y)
                lineTo(screenPoints[1].x, screenPoints[1].y)
                lineTo(screenPoints[2].x, screenPoints[2].y)
                lineTo(screenPoints[3].x, screenPoints[3].y)
                close()
            }
            drawPath(quadPath, cropAccent.copy(alpha = 0.07f))

            // 2) 3x3 Rule-of-Thirds grid displayed while user is adjusting corners
            if (activeCorner in 0..3) {
                val p0 = screenPoints[0] // top-left
                val p1 = screenPoints[1] // top-right
                val p2 = screenPoints[2] // bottom-right
                val p3 = screenPoints[3] // bottom-left
                val gridColor = Color.White.copy(alpha = 0.38f)
                val gridStroke = 1.25.dp.toPx()

                for (fraction in listOf(0.333f, 0.667f)) {
                    val topP = Offset(p0.x + (p1.x - p0.x) * fraction, p0.y + (p1.y - p0.y) * fraction)
                    val botP = Offset(p3.x + (p2.x - p3.x) * fraction, p3.y + (p2.y - p3.y) * fraction)
                    drawLine(gridColor, topP, botP, strokeWidth = gridStroke)

                    val leftP = Offset(p0.x + (p3.x - p0.x) * fraction, p0.y + (p3.y - p0.y) * fraction)
                    val rightP = Offset(p1.x + (p2.x - p1.x) * fraction, p1.y + (p2.y - p1.y) * fraction)
                    drawLine(gridColor, leftP, rightP, strokeWidth = gridStroke)
                }
            }

            // 3) Crop boundary lines
            for (i in 0..3) drawLine(cropAccent, screenPoints[i], screenPoints[(i + 1) % 4], strokeWidth = 3.25f)

            // 4) Corner handles with glow when dragged
            screenPoints.forEachIndexed { index, point ->
                val selected = index == activeCorner
                if (selected) {
                    drawCircle(selectedCornerColor.copy(alpha = 0.28f), radius = 28.dp.toPx(), center = point)
                }
                drawCircle(Color.Black.copy(alpha = 0.40f), radius = if (selected) 24.dp.toPx() else 19.dp.toPx(), center = point)
                drawCircle(Color.White, radius = if (selected) 17.5.dp.toPx() else 14.dp.toPx(), center = point)
                drawCircle(if (selected) selectedCornerColor else cropAccent,
                    radius = if (selected) 10.5.dp.toPx() else 8.5.dp.toPx(), center = point)
            }

            // 5) Live 2.5x magnifier loupe with drop shadow
            if (activeCorner in 0..3) {
                val corner = corners[activeCorner]
                val loupeRadius = 54.dp.toPx()
                val loupeCenter = Offset(
                    (size.width - loupeRadius - 12.dp.toPx()).coerceAtLeast(loupeRadius),
                    (loupeRadius + 12.dp.toPx()).coerceAtMost(size.height - loupeRadius)
                )
                val srcWidth = (loupeRadius * 2f / (scale * 2.5f)).roundToInt().coerceAtLeast(2)
                val srcHeight = srcWidth
                val srcLeft = (corner.x.roundToInt() - srcWidth / 2).coerceIn(0, (bitmap.width - srcWidth).coerceAtLeast(0))
                val srcTop = (corner.y.roundToInt() - srcHeight / 2).coerceIn(0, (bitmap.height - srcHeight).coerceAtLeast(0))
                val path = Path().apply { addOval(androidx.compose.ui.geometry.Rect(
                    loupeCenter.x - loupeRadius, loupeCenter.y - loupeRadius,
                    loupeCenter.x + loupeRadius, loupeCenter.y + loupeRadius
                )) }

                // Loupe shadow and border
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = loupeRadius + 6.dp.toPx(), center = loupeCenter)
                drawCircle(Color.White, radius = loupeRadius + 4.dp.toPx(), center = loupeCenter)
                clipPath(path) {
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        srcOffset = IntOffset(srcLeft, srcTop),
                        srcSize = IntSize(srcWidth.coerceAtMost(bitmap.width - srcLeft), srcHeight.coerceAtMost(bitmap.height - srcTop)),
                        dstOffset = IntOffset((loupeCenter.x - loupeRadius).roundToInt(), (loupeCenter.y - loupeRadius).roundToInt()),
                        dstSize = IntSize((loupeRadius * 2).roundToInt(), (loupeRadius * 2).roundToInt()),
                        filterQuality = FilterQuality.High
                    )
                }
                drawCircle(selectedCornerColor, radius = loupeRadius, center = loupeCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5.dp.toPx()))
                drawLine(selectedCornerColor, Offset(loupeCenter.x - 14.dp.toPx(), loupeCenter.y), Offset(loupeCenter.x + 14.dp.toPx(), loupeCenter.y), strokeWidth = 2.dp.toPx())
                drawLine(selectedCornerColor, Offset(loupeCenter.x, loupeCenter.y - 14.dp.toPx()), Offset(loupeCenter.x, loupeCenter.y + 14.dp.toPx()), strokeWidth = 2.dp.toPx())
            }
        }
    }
}
