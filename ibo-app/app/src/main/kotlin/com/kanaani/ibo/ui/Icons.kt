package com.kanaani.ibo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Icônes au trait, chemins SVG sur une grille 24×24. */
object Icons {
    const val Live = "M2 8h20v12H2zM7 3l5 5 5-5"
    const val Movies = "M4 4h16v16H4zM8 4v16M16 4v16M4 9h4M4 15h4M16 9h4M16 15h4"
    const val Series = "M3 8h18v12H3zM8 4h8"
    const val Download = "M12 4v11M7 10l5 5 5-5M5 20h14"
    const val Grid = "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z"
    const val Heart = "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"
    const val Account = "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 20a8 8 0 0 1 16 0"
    const val Settings = "M4 6h16M4 12h16M4 18h16M8 4v4M16 10v4M10 16v4"
    const val Globe = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"
    const val Refresh = "M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7"
    const val Info = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7.5v.5"
    const val Logout = "M15 4h4v16h-4M10 8l-4 4 4 4M6 12h11"
    const val Play = "M7 4v16l13-8z"
    const val Star = "M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z"
    const val Search = "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4"
}

@Composable
fun Icon(path: String, size: Dp, color: Color, stroke: Float = 2f, fill: Boolean = false, modifier: Modifier = Modifier) {
    val p = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            if (fill) drawPath(p, color)
            else drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
