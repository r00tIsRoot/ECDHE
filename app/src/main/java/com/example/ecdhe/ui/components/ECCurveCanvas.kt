package com.example.ecdhe.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecdhe.ecdhe.ConstructionStep
import com.example.ecdhe.ecdhe.ECPoint
import com.example.ecdhe.ecdhe.EllipticCurve
import com.example.ecdhe.ecdhe.StepOwner
import kotlin.math.abs
import kotlin.math.sqrt

/** 표시할 점의 종류 */
enum class PointType { GENERATOR, ALICE_PUBLIC, BOB_PUBLIC, SHARED_SECRET, INTERMEDIATE }

/** 그래프에 표시할 점 */
data class GraphPoint(
    val point: ECPoint,
    val label: String,
    val type: PointType
) {
    val color: Color get() = when (type) {
        PointType.GENERATOR -> Color(0xFF4CAF50)
        PointType.ALICE_PUBLIC -> Color(0xFF2196F3)
        PointType.BOB_PUBLIC -> Color(0xFFFF5722)
        PointType.SHARED_SECRET -> Color(0xFFE91E63)
        PointType.INTERMEDIATE -> Color(0xFF9E9E9E)
    }
}

/**
 * 타원곡선과 점들, construction 직선을 그리는 Canvas 컴포저블
 */
@Composable
fun ECCurveCanvas(
    curve: EllipticCurve,
    points: List<GraphPoint>,
    viewportX: ClosedFloatingPointRange<Double> = -3.0..5.0,
    viewportY: ClosedFloatingPointRange<Double> = -4.0..4.0,
    showGrid: Boolean = true,
    constructionSteps: List<ConstructionStep> = emptyList(),
    canvasHeight: androidx.compose.ui.unit.Dp = 320.dp,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
    ) {
        val padding = 40f
        val chartLeft = padding
        val chartRight = size.width - padding
        val chartTop = padding
        val chartBottom = size.height - padding
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        fun xToScreen(x: Double): Float =
            (chartLeft + ((x - viewportX.start) / (viewportX.endInclusive - viewportX.start)) * chartWidth).toFloat()
        fun yToScreen(y: Double): Float =
            (chartBottom - ((y - viewportY.start) / (viewportY.endInclusive - viewportY.start)) * chartHeight).toFloat()
        fun isVisible(p: ECPoint): Boolean =
            p.x >= viewportX.start && p.x <= viewportX.endInclusive &&
            p.y >= viewportY.start && p.y <= viewportY.endInclusive

        drawRect(color = Color(0xFFF5F5F5))

        if (showGrid) {
            drawGrid(viewportX, viewportY, chartLeft, chartRight, chartTop, chartBottom, ::xToScreen, ::yToScreen)
        }

        drawAxes(viewportX, viewportY, chartLeft, chartRight, chartTop, chartBottom, ::xToScreen, ::yToScreen)

        drawCurve(curve, viewportX, viewportY, ::xToScreen, ::yToScreen)

        val aliceColorStart = Color(0xFFEF9A9A)
        val aliceColorEnd   = Color(0xFFB71C1C)
        val bobColorStart   = Color(0xFF90CAF9)
        val bobColorEnd     = Color(0xFF0D47A1)

        val aliceSteps = constructionSteps.filter { it.owner == StepOwner.ALICE }
        val bobSteps   = constructionSteps.filter { it.owner == StepOwner.BOB }

        fun lerpColor(from: Color, to: Color, t: Float): Color {
            return Color(
                red   = from.red   + (to.red   - from.red)   * t,
                green = from.green + (to.green - from.green) * t,
                blue  = from.blue  + (to.blue  - from.blue)  * t,
                alpha = from.alpha + (to.alpha - from.alpha) * t
            )
        }

        fun drawOwnerSteps(steps: List<ConstructionStep>, colorStart: Color, colorEnd: Color) {
            val total = steps.size
            steps.forEachIndexed { idx, step ->
                val fraction = if (total <= 1) 1f else idx.toFloat() / (total - 1).toFloat()
                val color = lerpColor(colorStart, colorEnd, fraction)
                drawConstructionLine(step, color, ::xToScreen, ::yToScreen, ::isVisible, textMeasurer)
            }
        }

        drawOwnerSteps(aliceSteps, aliceColorStart, aliceColorEnd)
        drawOwnerSteps(bobSteps,   bobColorStart,   bobColorEnd)

        // 점들
        for (gp in points) {
            if (!gp.point.isInfinity) {
                drawPoint(gp, ::xToScreen, ::yToScreen, textMeasurer)
            }
        }
    }
}

private fun DrawScope.drawGrid(
    viewportX: ClosedFloatingPointRange<Double>,
    viewportY: ClosedFloatingPointRange<Double>,
    chartLeft: Float, chartRight: Float, chartTop: Float, chartBottom: Float,
    xToScreen: (Double) -> Float,
    yToScreen: (Double) -> Float
) {
    val gridColor = Color(0xFFE0E0E0)

    val xStep = calcStep(viewportX.endInclusive - viewportX.start)
    var x = ceilToStep(viewportX.start, xStep)
    while (x <= viewportX.endInclusive) {
        if (abs(x) > 1e-10) {
            val sx = xToScreen(x)
            drawLine(gridColor, Offset(sx, chartTop), Offset(sx, chartBottom), strokeWidth = 1f)
        }
        x += xStep
    }

    val yStep = calcStep(viewportY.endInclusive - viewportY.start)
    var y = ceilToStep(viewportY.start, yStep)
    while (y <= viewportY.endInclusive) {
        if (abs(y) > 1e-10) {
            val sy = yToScreen(y)
            drawLine(gridColor, Offset(chartLeft, sy), Offset(chartRight, sy), strokeWidth = 1f)
        }
        y += yStep
    }
}

private fun DrawScope.drawAxes(
    viewportX: ClosedFloatingPointRange<Double>,
    viewportY: ClosedFloatingPointRange<Double>,
    chartLeft: Float, chartRight: Float, chartTop: Float, chartBottom: Float,
    xToScreen: (Double) -> Float,
    yToScreen: (Double) -> Float
) {
    val axisColor = Color(0xFF333333)

    if (viewportY.start <= 0.0 && viewportY.endInclusive >= 0.0) {
        val sy = yToScreen(0.0)
        drawLine(axisColor, Offset(chartLeft, sy), Offset(chartRight, sy),
            strokeWidth = 2f, cap = StrokeCap.Round)
    }
    if (viewportX.start <= 0.0 && viewportX.endInclusive >= 0.0) {
        val sx = xToScreen(0.0)
        drawLine(axisColor, Offset(sx, chartTop), Offset(sx, chartBottom),
            strokeWidth = 2f, cap = StrokeCap.Round)
    }

    val arrowSize = 8f
    val sy = syAxis(viewportY, yToScreen)
    val sx = sxAxis(viewportX, xToScreen)
    drawLine(axisColor, Offset(chartRight - arrowSize, sy - arrowSize),
        Offset(chartRight, sy), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(axisColor, Offset(chartRight - arrowSize, sy + arrowSize),
        Offset(chartRight, sy), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(axisColor, Offset(sx - arrowSize, chartTop + arrowSize),
        Offset(sx, chartTop), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(axisColor, Offset(sx + arrowSize, chartTop + arrowSize),
        Offset(sx, chartTop), strokeWidth = 2f, cap = StrokeCap.Round)
}

private fun DrawScope.syAxis(viewportY: ClosedFloatingPointRange<Double>, yToScreen: (Double) -> Float): Float {
    return if (viewportY.start <= 0.0 && viewportY.endInclusive >= 0.0) yToScreen(0.0) else yToScreen(viewportY.start)
}

private fun DrawScope.sxAxis(viewportX: ClosedFloatingPointRange<Double>, xToScreen: (Double) -> Float): Float {
    return if (viewportX.start <= 0.0 && viewportX.endInclusive >= 0.0) xToScreen(0.0) else xToScreen(viewportX.start)
}

private fun DrawScope.drawCurve(
    curve: EllipticCurve,
    viewportX: ClosedFloatingPointRange<Double>,
    viewportY: ClosedFloatingPointRange<Double>,
    xToScreen: (Double) -> Float,
    yToScreen: (Double) -> Float
) {
    val curveColor = Color(0xFF1565C0)
    val curveStroke = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    val samples = 400
    val upperPath = Path()
    val lowerPath = Path()
    var upperStarted = false
    var lowerStarted = false

    for (i in 0..samples) {
        val x = viewportX.start + (i.toDouble() / samples) * (viewportX.endInclusive - viewportX.start)
        val y2 = curve.ySquared(x)

        if (y2 >= 0) {
            val y = sqrt(y2)
            val sx = xToScreen(x)
            val syPos = yToScreen(y)
            val syNeg = yToScreen(-y)

            if (y >= viewportY.start && y <= viewportY.endInclusive) {
                if (!upperStarted) { upperPath.moveTo(sx, syPos); upperStarted = true }
                else { upperPath.lineTo(sx, syPos) }
            } else { upperStarted = false }

            if (-y >= viewportY.start && -y <= viewportY.endInclusive) {
                if (!lowerStarted) { lowerPath.moveTo(sx, syNeg); lowerStarted = true }
                else { lowerPath.lineTo(sx, syNeg) }
            } else { lowerStarted = false }
        } else {
            upperStarted = false; lowerStarted = false
        }
    }

    drawPath(upperPath, curveColor, style = curveStroke)
    drawPath(lowerPath, curveColor, style = curveStroke)
}

private fun DrawScope.drawConstructionLine(
    step: ConstructionStep,
    lineColor: Color,
    xToScreen: (Double) -> Float,
    yToScreen: (Double) -> Float,
    isVisible: (ECPoint) -> Boolean,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)

    val xMin = -4.0
    val xMax = 5.0
    val y1 = step.slope * xMin + step.intercept
    val y2 = step.slope * xMax + step.intercept

    drawLine(
        color = lineColor,
        start = Offset(xToScreen(xMin), yToScreen(y1)),
        end = Offset(xToScreen(xMax), yToScreen(y2)),
        strokeWidth = 2.5f,
        pathEffect = dashEffect,
        cap = StrokeCap.Round
    )

    val labelStyle = TextStyle(fontSize = 10.sp, color = lineColor, fontWeight = FontWeight.Bold)

    for ((i, p) in listOf(step.point1, step.point2).withIndex()) {
        if (!p.isInfinity && isVisible(p)) {
            val sx = xToScreen(p.x)
            val sy = yToScreen(p.y)
            drawCircle(color = lineColor, radius = 5f, center = Offset(sx, sy))
            drawCircle(color = Color.White, radius = 3f, center = Offset(sx, sy))

            val inputLabel = if (step.point1 == step.point2) "P" else if (i == 0) "P₁" else "P₂"
            val labelResult = textMeasurer.measure(
                text = inputLabel, style = labelStyle
            )
            drawText(textLayoutResult = labelResult, topLeft = Offset(sx + 6f, sy - 12f))
        }
    }

    if (!step.reflectedPoint.isInfinity && isVisible(step.reflectedPoint)) {
        val sx = xToScreen(step.reflectedPoint.x)
        val sy = yToScreen(step.reflectedPoint.y)
        drawCircle(color = lineColor.copy(alpha = 0.4f), radius = 6f, center = Offset(sx, sy))

        val labelResult = textMeasurer.measure(
            text = "교점", style = labelStyle.copy(color = lineColor.copy(alpha = 0.6f))
        )
        drawText(textLayoutResult = labelResult, topLeft = Offset(sx + 7f, sy - 12f))
    }

    if (!step.resultPoint.isInfinity && isVisible(step.resultPoint)) {
        val sx = xToScreen(step.resultPoint.x)
        val sy = yToScreen(step.resultPoint.y)
        drawCircle(color = lineColor, radius = 7f, center = Offset(sx, sy))
        drawCircle(color = Color.White, radius = 4f, center = Offset(sx, sy))
        drawCircle(color = lineColor, radius = 2f, center = Offset(sx, sy))

        val resultLabel = if (step.isTangent) "2P" else "P+Q"
        val labelResult = textMeasurer.measure(text = resultLabel, style = labelStyle)
        drawText(textLayoutResult = labelResult, topLeft = Offset(sx + 8f, sy - 7f))
    }
}

private fun DrawScope.drawPoint(
    gp: GraphPoint,
    xToScreen: (Double) -> Float,
    yToScreen: (Double) -> Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val sx = xToScreen(gp.point.x)
    val sy = yToScreen(gp.point.y)
    val radius = if (gp.type == PointType.GENERATOR) 10f else 8f
    val borderWidth = 3f

    drawCircle(color = gp.color, radius = radius + borderWidth, center = Offset(sx, sy))
    drawCircle(color = Color.White, radius = radius, center = Offset(sx, sy))
    drawCircle(color = gp.color, radius = radius * 0.5f, center = Offset(sx, sy))

    val textResult = textMeasurer.measure(
        text = gp.label,
        style = TextStyle(fontSize = 11.sp, color = gp.color)
    )
    val labelOffset = Offset(sx + radius + 6f, sy - radius - textResult.size.height / 2f)
    drawText(textLayoutResult = textResult, topLeft = labelOffset)
}

private fun calcStep(range: Double): Double {
    val rough = range / 6.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(rough)))
    val normalized = rough / magnitude
    return magnitude * when {
        normalized < 1.5 -> 1.0
        normalized < 3.5 -> 2.0
        normalized < 7.5 -> 5.0
        else -> 10.0
    }
}

private fun ceilToStep(value: Double, step: Double): Double {
    return Math.ceil(value / step) * step
}
