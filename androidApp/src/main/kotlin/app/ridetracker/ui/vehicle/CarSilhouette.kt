package app.ridetracker.ui.vehicle

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import app.ridetracker.shared.domain.BodyType

/** Car colours offered for the silhouette. */
val carColorPresets: List<Long> = listOf(
    0xFFF4F4F2, // white
    0xFF1F2124, // black
    0xFFB9BFC5, // silver
    0xFF6C737A, // grey
    0xFF1F4E9C, // blue
    0xFFB3261E, // red
    0xFF2E6B3F, // green
    0xFFD9C8A4, // beige
    0xFF6B4A33, // brown
    0xFFF2C230, // yellow
    0xFFE07A1F, // orange
)

/** Side-view outline per body type, drawn on a 120 × 50 grid (ground at y = 48). */
private class Shape(val body: String, val windows: List<String>, val wheelsX: Pair<Float, Float>, val wheelRadius: Float = 8f)

private val shapes = mapOf(
    BodyType.HATCHBACK to Shape(
        body = "M8,38 L8,30 Q9,26 16,25 L34,23 L46,12 Q48,10 52,10 L86,10 Q92,10 96,16 L104,26 Q108,28 108,32 L108,38 Z",
        windows = listOf("M38,23 L48,13 L64,13 L64,23 Z", "M67,13 L85,13 Q90,13 93,17 L98,23 L67,23 Z"),
        wheelsX = 28f to 88f,
    ),
    BodyType.SEDAN to Shape(
        body = "M6,38 L6,30 Q7,26 14,25 L34,23 L46,13 Q48,11 52,11 L78,11 Q82,11 85,14 L95,23 L110,25 Q115,26 115,30 L115,38 Z",
        windows = listOf("M38,23 L48,14 L62,14 L62,23 Z", "M65,14 L77,14 Q80,14 82,16 L89,23 L65,23 Z"),
        wheelsX = 28f to 94f,
    ),
    BodyType.ESTATE to Shape(
        body = "M6,38 L6,30 Q7,26 14,25 L32,23 L44,12 Q46,10 50,10 L104,10 Q109,10 111,14 L114,24 Q116,27 116,31 L116,38 Z",
        windows = listOf("M36,23 L46,13 L66,13 L66,23 Z", "M69,13 L90,13 L90,23 L69,23 Z", "M93,13 L106,13 Q108,13 109,15 L111,23 L93,23 Z"),
        wheelsX = 28f to 96f,
    ),
    BodyType.SUV to Shape(
        body = "M6,38 L6,26 Q7,22 14,21 L30,19 L40,8 Q42,6 46,6 L102,6 Q107,6 109,10 L113,20 Q115,23 115,27 L115,38 Z",
        windows = listOf("M34,19 L43,9 L64,9 L64,19 Z", "M67,9 L88,9 L88,19 L67,19 Z", "M91,9 L103,9 Q105,9 106,11 L109,19 L91,19 Z"),
        wheelsX = 29f to 94f,
        wheelRadius = 9f,
    ),
    BodyType.MPV to Shape(
        body = "M6,38 L6,28 Q7,24 12,23 L24,19 L40,7 Q42,5 46,5 L104,5 Q109,5 111,9 L114,20 Q116,24 116,28 L116,38 Z",
        windows = listOf("M30,19 L44,8 L62,8 L62,19 Z", "M65,8 L84,8 L84,19 L65,19 Z", "M87,8 L104,8 Q106,8 107,10 L110,19 L87,19 Z"),
        wheelsX = 28f to 96f,
    ),
    BodyType.VAN to Shape(
        body = "M6,38 L6,26 Q7,20 14,18 L24,8 Q26,5 30,5 L112,5 Q115,5 115,8 L115,38 Z",
        windows = listOf("M18,18 L27,9 L40,9 L40,18 Z"),
        wheelsX = 26f to 96f,
    ),
)

private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

/**
 * Generic side-view car drawn from vectors: body in the car's colour, glass, wheels.
 * Offline, free and tiny, so no car model ever leaves the phone.
 */
@Composable
fun CarSilhouette(bodyType: BodyType, colorArgb: Long?, modifier: Modifier = Modifier) {
    val shape = shapes.getValue(bodyType)
    val body = remember(bodyType) { parse(shape.body) }
    val windows = remember(bodyType) { shape.windows.map(::parse) }
    val carColor = Color(colorArgb ?: carColorPresets[2])
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val glass = if (carColor.luminance() > 0.6f) Color(0xFF8FA8BA) else Color(0xFFC7DAE6)
    val tyre = Color(0xFF26292C)
    val hub = Color(0xFFB0B6BC)
    Canvas(modifier) {
        val s = minOf(size.width / 120f, size.height / 50f)
        translate(left = (size.width - 120f * s) / 2f, top = (size.height - 50f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                drawPath(body, carColor)
                // Thin outline so light cars stay visible on light backgrounds (and dark on dark).
                drawPath(body, outline.copy(alpha = 0.6f), style = Stroke(width = 1.2f))
                windows.forEach { drawPath(it, glass) }
                listOf(shape.wheelsX.first, shape.wheelsX.second).forEach { x ->
                    drawCircle(surface, radius = shape.wheelRadius + 1.5f, center = Offset(x, 40f))
                    drawCircle(tyre, radius = shape.wheelRadius, center = Offset(x, 40f))
                    drawCircle(hub, radius = shape.wheelRadius * 0.42f, center = Offset(x, 40f))
                }
            }
        }
    }
}
