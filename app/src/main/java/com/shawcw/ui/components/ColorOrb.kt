package com.shawcw.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The central feedback element. Idle it is a dim disc; when a tone is present it
 * fills with [toneColor], brightens and swells.
 *
 * The on/off envelope is intentionally instant, not eased, so the orb tracks
 * fast CW exactly like the flash rather than smearing dits together. Only the
 * lit hue is crossfaded, and only briefly, so drifting off frequency shifts
 * color smoothly without lagging the keying.
 */
@Composable
fun ColorOrb(
    active: Boolean,
    toneColor: Color,
    modifier: Modifier = Modifier,
) {
    val idle = Color(0xFF18203B)

    val litColor by animateColorAsState(
        targetValue = toneColor,
        animationSpec = tween(durationMillis = 40),
        label = "orbHue",
    )

    // Snap the envelope to the keying; the flash does the same.
    val color = if (active) litColor else idle
    val scale = if (active) 1f else 0.86f
    val glow = if (active) 0.55f else 0f

    Canvas(modifier = modifier.size(220.dp)) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        val radius = maxRadius * scale

        // Outer glow halo.
        if (glow > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = glow), Color.Transparent),
                    center = center,
                    radius = maxRadius,
                ),
                radius = maxRadius,
                center = center,
            )
        }

        // Core orb with a soft highlight toward the top.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = 0.82f)),
                center = androidx.compose.ui.geometry.Offset(center.x, center.y - radius * 0.3f),
                radius = radius,
            ),
            radius = radius * 0.78f,
            center = center,
        )
    }
}
