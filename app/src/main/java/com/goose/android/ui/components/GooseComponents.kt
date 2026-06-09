package com.goose.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goose.android.data.*
import com.goose.android.ui.theme.GooseColors
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Goose Component Library — Bevel/WHOOP-inspired premium dark health UI
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The main recovery ring — circular progress arc with score in center.
 * Mirrors WHOOP's recovery ring and Bevel's design language.
 */
@Composable
fun RecoveryRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 16.dp
) {
    val ringColor = when {
        score >= 67 -> GooseColors.RecoveryGreen
        score >= 34 -> GooseColors.RecoveryYellow
        else -> GooseColors.RecoveryRed
    }

    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 1200, easing = EaseOutCubic),
        label = "recoveryRing"
    )

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.minDimension - strokeWidth.toPx()) / 2f
            val strokePx = strokeWidth.toPx()

            // Background track
            drawCircle(
                color = Color(0xFF1A2228),
                radius = radius,
                center = center,
                style = Stroke(width = strokePx)
            )

            // Progress arc (start from top = -90°)
            val sweepAngle = animatedProgress * 360f
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        ringColor.copy(alpha = 0.6f),
                        ringColor,
                        ringColor
                    ),
                    center = center
                ),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )

            // Glow dot at arc tip
            if (animatedProgress > 0.01f) {
                val tipAngle = Math.toRadians((-90f + sweepAngle).toDouble())
                val tipX = (center.x + radius * cos(tipAngle)).toFloat()
                val tipY = (center.y + radius * sin(tipAngle)).toFloat()
                drawCircle(
                    color = ringColor,
                    radius = strokePx / 2f + 2f,
                    center = Offset(tipX, tipY)
                )
                drawCircle(
                    color = ringColor.copy(alpha = 0.3f),
                    radius = strokePx,
                    center = Offset(tipX, tipY)
                )
            }
        }

        // Center content
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (score > 0) "$score%" else "—",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = ringColor,
                    fontSize = if (size >= 180.dp) 40.sp else 28.sp
                )
            )
            Text(
                text = "RECOVERY",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = GooseColors.TextSecondary,
                    letterSpacing = 2.sp
                )
            )
        }
    }
}

/**
 * Sleep stage timeline bar — horizontal bar showing sleep stage breakdown.
 * Inspired by Bevel's sleep visualization.
 */
@Composable
fun SleepTimelineBar(
    deepMinutes: Int,
    remMinutes: Int,
    lightMinutes: Int,
    awakeMinutes: Int,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp
) {
    val total = (deepMinutes + remMinutes + lightMinutes + awakeMinutes).coerceAtLeast(1)

    val segments = listOf(
        deepMinutes to GooseColors.SleepDeep,
        remMinutes to GooseColors.SleepREM,
        lightMinutes to GooseColors.SleepLight,
        awakeMinutes to GooseColors.SleepAwake
    )

    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
    ) {
        segments.forEach { (minutes, color) ->
            if (minutes > 0) {
                Box(
                    modifier = Modifier
                        .weight(minutes.toFloat() / total)
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
}

/**
 * Heart rate mini chart — small sparkline of HR over time.
 */
@Composable
fun HeartRateSparkline(
    samples: List<Int>,
    modifier: Modifier = Modifier,
    lineColor: Color = GooseColors.RedMetric,
    height: Dp = 48.dp
) {
    if (samples.isEmpty()) return

    val minHR = samples.min().toFloat()
    val maxHR = samples.max().toFloat()
    val range = (maxHR - minHR).coerceAtLeast(1f)

    Canvas(modifier = modifier.height(height)) {
        val w = this.size.width
        val h = this.size.height
        val stepX = w / (samples.size - 1).coerceAtLeast(1)

        val points = samples.mapIndexed { i, hr ->
            val x = i * stepX
            val y = h - ((hr - minHR) / range * h)
            Offset(x, y)
        }

        // Gradient fill
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points.first().x, h)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, h)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent)
                )
            )

            // Line
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = lineColor,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Metric card — the standard card used throughout the health dashboard.
 */
@Composable
fun GooseMetricCard(
    label: String,
    value: String,
    unit: String = "",
    valueColor: Color = GooseColors.TextPrimary,
    subtitle: String? = null,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = GooseColors.CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GooseColors.CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = GooseColors.TextSecondary,
                        letterSpacing = 1.5.sp
                    )
                )
                badge?.let {
                    Box(
                        modifier = Modifier
                            .background(GooseColors.SurfaceVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = GooseColors.TextSecondary
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = valueColor
                    )
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = GooseColors.TextSecondary
                        )
                    )
                }
            }

            subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = GooseColors.TextTertiary
                    )
                )
            }

            content?.let {
                Spacer(Modifier.height(12.dp))
                it()
            }
        }
    }
}

/**
 * Section header with optional trailing action.
 */
@Composable
fun GooseSectionHeader(
    title: String,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                color = GooseColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        )
        trailingText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = GooseColors.GooseBlue
                ),
                modifier = if (onTrailingClick != null)
                    Modifier.clickable(onClick = onTrailingClick)
                else Modifier
            )
        }
    }
}

/**
 * Status dot — pulsing animated dot indicating device connection state.
 */
@Composable
fun ConnectionStatusDot(
    isConnected: Boolean,
    isAnimating: Boolean = false,
    size: Dp = 8.dp
) {
    val color = if (isConnected) GooseColors.GreenMetric else GooseColors.TextTertiary
    val alpha by if (isAnimating) {
        rememberInfiniteTransition(label = "dot").animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(color.copy(alpha = alpha), CircleShape)
    )
}

/**
 * Stat row — used inside cards for sub-metrics.
 */
@Composable
fun GooseStatRow(
    label: String,
    value: String,
    unit: String = "",
    valueColor: Color = GooseColors.TextPrimary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = GooseColors.TextSecondary
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor
                )
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = GooseColors.TextSecondary
                    )
                )
            }
        }
    }
}

/**
 * Divider styled for Goose dark theme.
 */
@Composable
fun GooseDivider(modifier: Modifier = Modifier) {
    Divider(
        modifier = modifier,
        color = GooseColors.CardBorder,
        thickness = 0.5.dp
    )
}

/**
 * Empty / unavailable state placeholder.
 */
@Composable
fun GooseEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                color = GooseColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = GooseColors.TextTertiary,
                textAlign = TextAlign.Center
            )
        )
    }
}
