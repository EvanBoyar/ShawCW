package com.shawcw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.shawcw.SpectrumState
import kotlin.math.max

/**
 * Live bar view of the band: one bar per detector bin, height scaled to the
 * strongest bin in the snapshot. The noise floor is drawn as a dashed line so
 * it is clear how far a tone stands above the background.
 */
@Composable
fun SpectrumView(
    spectrum: SpectrumState,
    barColor: Color,
    floorColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val mags = spectrum.magnitudes
        if (mags.isEmpty()) return@Canvas

        var peak = spectrum.noiseFloor
        for (m in mags) peak = max(peak, m)
        val scale = if (peak > 0.0) 1.0 / peak else 0.0

        drawBars(mags, scale, barColor)
        drawFloorLine(spectrum.noiseFloor, scale, floorColor)
    }
}

private fun DrawScope.drawBars(mags: DoubleArray, scale: Double, color: Color) {
    val gap = size.width * 0.012f
    val barWidth = (size.width - gap * (mags.size - 1)) / mags.size
    val minBar = size.height * 0.02f
    for (i in mags.indices) {
        val h = max(minBar, (mags[i] * scale).toFloat() * size.height)
        val left = i * (barWidth + gap)
        drawRoundRectBar(left, barWidth, h, color)
    }
}

private fun DrawScope.drawRoundRectBar(left: Float, width: Float, height: Float, color: Color) {
    val radius = androidx.compose.ui.geometry.CornerRadius(width * 0.35f, width * 0.35f)
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(left, size.height - height),
        size = androidx.compose.ui.geometry.Size(width, height),
        cornerRadius = radius,
    )
}

private fun DrawScope.drawFloorLine(floor: Double, scale: Double, color: Color) {
    val y = size.height - (floor * scale).toFloat() * size.height
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(0f, y),
        end = androidx.compose.ui.geometry.Offset(size.width, y),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
    )
}
