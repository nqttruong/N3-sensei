package com.n3sensei.app.app.translate

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A rectangle in screen pixels (right and bottom exclusive). */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** A point in screen pixels: the top-left corner of the popup window. */
data class PxPoint(val x: Int, val y: Int)

/**
 * Where the floating "Dịch nhanh" popup may be. Plain arithmetic (no Android), so the rules are
 * unit-tested:
 *
 * - The popup always stays wholly inside the safe area (screen minus status bar, navigation bar
 *   and display cutout), so its buttons and its drag handle stay reachable. A popup larger than
 *   the safe area keeps its top-left corner inside.
 * - Its place is remembered as a fraction of the free room (0 = left/top, 1 = right/bottom), so
 *   after a rotation or a window size change it lands at the same relative place.
 */
object OverlayGeometry {

    /** First place: right side, near the top (the selection is usually lower, under the reader's eyes). */
    val DEFAULT_PLACE = Pair(1f, 0.12f)

    /** [point] moved, if needed, so a popup of [width] × [height] is inside [safe]. */
    fun clamp(point: PxPoint, width: Int, height: Int, safe: PxRect): PxPoint {
        val maxX = maxOf(safe.left, safe.right - width)
        val maxY = maxOf(safe.top, safe.bottom - height)
        return PxPoint(point.x.coerceIn(safe.left, maxX), point.y.coerceIn(safe.top, maxY))
    }

    /** [point] as fractions of the free room in [safe] (0..1 each). */
    fun toPlace(point: PxPoint, width: Int, height: Int, safe: PxRect): Pair<Float, Float> {
        val p = clamp(point, width, height, safe)
        val roomX = safe.width - width
        val roomY = safe.height - height
        return Pair(
            if (roomX > 0) (p.x - safe.left).toFloat() / roomX else 0f,
            if (roomY > 0) (p.y - safe.top).toFloat() / roomY else 0f,
        )
    }

    /** The point at the fractions [place] of the free room in [safe] (always inside). */
    fun fromPlace(place: Pair<Float, Float>, width: Int, height: Int, safe: PxRect): PxPoint {
        val roomX = maxOf(0, safe.width - width)
        val roomY = maxOf(0, safe.height - height)
        val point = PxPoint(
            safe.left + (place.first.coerceIn(0f, 1f) * roomX).roundToInt(),
            safe.top + (place.second.coerceIn(0f, 1f) * roomY).roundToInt(),
        )
        return clamp(point, width, height, safe)
    }
}

/**
 * Tells a drag of the popup's header from a tap, from raw screen coordinates of the finger (the
 * window moves under the finger, so its local coordinates cannot be used).
 *
 * The popup moves only once the finger has gone farther than [touchSlop] from where it went
 * down; from then on it follows the finger exactly (no jump by the slop).
 */
class DragTracker(private val touchSlop: Float) {
    private var downX = 0f
    private var downY = 0f
    private var start = PxPoint(0, 0)
    private var tracking = false

    /** True once the finger went past the slop in this gesture. */
    var dragging = false
        private set

    fun down(rawX: Float, rawY: Float, window: PxPoint) {
        downX = rawX
        downY = rawY
        start = window
        tracking = true
        dragging = false
    }

    /** The window's new top-left corner, or null while it is still a tap (or no gesture). */
    fun move(rawX: Float, rawY: Float): PxPoint? {
        if (!tracking) return null
        val dx = rawX - downX
        val dy = rawY - downY
        if (!dragging && hypot(dx, dy) <= touchSlop) return null
        dragging = true
        return PxPoint(start.x + dx.roundToInt(), start.y + dy.roundToInt())
    }

    /** Ends the gesture; true when it was a drag (not a tap). */
    fun up(): Boolean {
        val dragged = dragging
        tracking = false
        dragging = false
        return dragged
    }

    companion object {
        /** True when two points differ (a cheap check before asking the window manager to move). */
        fun moved(a: PxPoint, b: PxPoint): Boolean = abs(a.x - b.x) + abs(a.y - b.y) > 0
    }
}
