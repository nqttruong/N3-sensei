package com.n3sensei.app.app.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGeometryTest {

    /** A 1080 × 2400 portrait phone: 120 px status bar, 60 px navigation bar. */
    private val portrait = PxRect(0, 120, 1080, 2340)

    /** The same phone turned: bars on the side. */
    private val landscape = PxRect(120, 0, 2340, 1080)

    private val w = 700
    private val h = 400

    private fun inside(p: PxPoint, safe: PxRect) =
        p.x >= safe.left && p.y >= safe.top && p.x + w <= safe.right && p.y + h <= safe.bottom

    @Test
    fun aPopupDraggedPastAnyEdge_staysWhollyOnScreen() {
        assertEquals(PxPoint(0, 120), OverlayGeometry.clamp(PxPoint(-500, -900), w, h, portrait)) // up and left
        assertEquals(PxPoint(380, 1940), OverlayGeometry.clamp(PxPoint(5000, 9000), w, h, portrait)) // down and right
        assertEquals(PxPoint(100, 500), OverlayGeometry.clamp(PxPoint(100, 500), w, h, portrait)) // free room: as asked
        // Never under the status bar or the navigation bar.
        assertTrue(inside(OverlayGeometry.clamp(PxPoint(380, 2339), w, h, portrait), portrait))
    }

    @Test
    fun aPopupLargerThanTheScreen_keepsItsTopLeftInside() {
        // A huge font scale: the header (drag handle, X) must stay reachable.
        assertEquals(PxPoint(0, 120), OverlayGeometry.clamp(PxPoint(300, 900), 1200, 2600, portrait))
    }

    @Test
    fun theFirstPlace_isTopRight_insideTheSafeArea() {
        val p = OverlayGeometry.fromPlace(OverlayGeometry.DEFAULT_PLACE, w, h, portrait)
        assertEquals(380, p.x) // right edge
        assertTrue(p.y in 120..600) // near the top
        assertTrue(inside(p, portrait))
    }

    @Test
    fun afterARotation_theSamePlace_isFoundInTheNewBounds() {
        val leftLow = PxPoint(0, 1940) // bottom-left in portrait
        val place = OverlayGeometry.toPlace(leftLow, w, h, portrait)
        assertEquals(Pair(0f, 1f), place)
        val turned = OverlayGeometry.fromPlace(place, w, h, landscape)
        assertEquals(PxPoint(120, 680), turned) // bottom-left, beside the bar
        assertTrue(inside(turned, landscape))
        // Back to portrait: where it was.
        assertEquals(leftLow, OverlayGeometry.fromPlace(OverlayGeometry.toPlace(turned, w, h, landscape), w, h, portrait))
    }

    @Test
    fun aTap_doesNotMoveIt_aDragFollowsTheFingerWithoutAJump() {
        val drag = DragTracker(touchSlop = 24f)
        drag.down(500f, 300f, PxPoint(380, 200))
        assertNull(drag.move(510f, 310f)) // within the slop: still a tap
        assertFalse(drag.up())

        drag.down(500f, 300f, PxPoint(380, 200))
        assertNull(drag.move(520f, 300f))
        assertEquals(PxPoint(330, 260), drag.move(450f, 360f)) // past the slop: the whole way, not minus the slop
        assertEquals(PxPoint(-120, 1960), drag.move(0f, 2060f)) // a fast fling: the raw position (kept inside by clamp)
        assertTrue(drag.up())
        assertNull(drag.move(600f, 600f)) // no gesture: nothing moves
    }

    @Test
    fun dragsInEveryDirection() {
        val drag = DragTracker(touchSlop = 8f)
        val start = PxPoint(200, 800)
        fun dragTo(dx: Float, dy: Float): PxPoint {
            drag.down(400f, 900f, start)
            return drag.move(400f + dx, 900f + dy)!!.also { drag.up() }
        }
        assertEquals(PxPoint(200, 500), dragTo(0f, -300f)) // up
        assertEquals(PxPoint(200, 1100), dragTo(0f, 300f)) // down
        assertEquals(PxPoint(50, 800), dragTo(-150f, 0f)) // left
        assertEquals(PxPoint(350, 800), dragTo(150f, 0f)) // right
    }
}
