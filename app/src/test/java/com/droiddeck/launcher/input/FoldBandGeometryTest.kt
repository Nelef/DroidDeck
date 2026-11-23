package com.droiddeck.launcher.input

import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FoldBandGeometryTest {
    // An unfolded Galaxy Z Fold7: 2184 x 1968 at 367.55 dpi; a 16:9 game across the top leaves a 2184 x 740 band
    private val width = 2184
    private val height = 740
    private val xdpi = 367.55f
    private val dock = OnScreenLayout.Dock(portrait = false, grips = true)

    private fun stock() = listOf(InputControl.LS, InputControl.RS, InputControl.A, InputControl.B, InputControl.X, InputControl.Y,
        InputControl.UP, InputControl.RIGHT, InputControl.DOWN, InputControl.LEFT, InputControl.LB, InputControl.LT, InputControl.RB,
        InputControl.RT, InputControl.SELECT, InputControl.START, InputControl.L4, InputControl.R4).map {
        InputSurfaceElement(it, .1f, .1f, .05f, .05f, buttonShape = InputButtonShape.ROUNDED)
    } + InputSurfaceElement(InputControl.A, .5f, .5f, .1f, .1f, id = "custom:jump")

    private fun placed() = FoldBandGeometry(width, height, Rect(), xdpi).place(stock(), dock.left, dock.right, DpadStyle.CROSS)

    private fun box(e: InputSurfaceElement) = floatArrayOf(e.x * width, e.y * height, e.width * width, e.height * height)
    private fun centre(e: InputSurfaceElement) = box(e).let { floatArrayOf(it[0] + it[2] / 2f, it[1] + it[3] / 2f) }
    private fun List<InputSurfaceElement>.of(c: InputControl) = first { it.id == "legacy:${c.id}" }

    @Test fun takesTheBandOnAFoldableOnly() {
        assertTrue(FoldBandGeometry.applies(dock, width, height))
        assertFalse(FoldBandGeometry.applies(dock.copy(portrait = true), width, height))
        assertFalse(FoldBandGeometry.applies(dock, 740, 2184))
        assertFalse(FoldBandGeometry.applies(null, width, height))
    }

    @Test fun matchesNx2androidsBandOnTheFold7() {
        val g = FoldBandGeometry(width, height, Rect(), xdpi)
        assertEquals(1.15f, g.scale, .01f)   // nx2android's band: x1.15, face buttons 8.6 mm, left stick 22.8 mm (the band's width stops the growth)
        val e = placed()
        val face = box(e.of(InputControl.A))[2]
        assertEquals(8.6f, face / xdpi * 25.4f, .1f)
        assertEquals(22.8f, box(e.of(InputControl.LS))[2] / xdpi * 25.4f, .1f)
        // Switch positions: B (Switch's A) on the right of the diamond, A at its bottom, Y on top, X on the left
        assertTrue(centre(e.of(InputControl.B))[0] > centre(e.of(InputControl.A))[0])
        assertTrue(centre(e.of(InputControl.A))[1] > centre(e.of(InputControl.B))[1])
        assertTrue(centre(e.of(InputControl.Y))[1] < centre(e.of(InputControl.B))[1])
        assertTrue(centre(e.of(InputControl.X))[0] < centre(e.of(InputControl.A))[0])
        // left stick above the d-pad, face buttons above the right stick
        assertTrue(centre(e.of(InputControl.LS))[1] < centre(e.of(InputControl.DOWN))[1])
        assertTrue(centre(e.of(InputControl.B))[1] < centre(e.of(InputControl.RS))[1])
        // nx2android's centres in the band: left stick (268, 430), A's slot (1996, 443)
        assertEquals(268f, centre(e.of(InputControl.LS))[0], 2f); assertEquals(430f, centre(e.of(InputControl.LS))[1], 2f)
        assertEquals(1996f, centre(e.of(InputControl.B))[0], 2f); assertEquals(443f, centre(e.of(InputControl.B))[1], 2f)
    }

    @Test fun keepsEverythingOnTheBandAndApart() {
        val e = placed().filter { it.id.startsWith("legacy:") }
        for (c in e) {
            val b = box(c)
            assertTrue("${c.id} on the band", b[0] >= -1f && b[1] >= -1f && b[0] + b[2] <= width + 1f && b[1] + b[3] <= height + 1f)
        }
        fun round(b: FloatArray) = abs(b[2] - b[3]) < 2f   // a square box holds a round control: the face diamond's boxes meet at their corners
        for (i in e.indices) for (j in i + 1 until e.size) {
            val a = box(e[i]); val b = box(e[j])
            val overlap = if (round(a) && round(b)) {
                hypot(a[0] + a[2] / 2f - b[0] - b[2] / 2f, a[1] + a[3] / 2f - b[1] - b[3] / 2f) < (a[2] + b[2]) / 2f - 1f
            } else a[0] < b[0] + b[2] - 1f && b[0] < a[0] + a[2] - 1f && a[1] < b[1] + b[3] - 1f && b[1] < a[1] + a[3] - 1f
            assertFalse("${e[i].id} and ${e[j].id} overlap", overlap)
        }
    }

    @Test fun leavesTheRearGripsToTheirTabAndKeepsCustomButtons() {
        val e = placed()
        assertTrue(e.none { it.control == InputControl.L4 || it.control == InputControl.R4 })
        assertEquals(.5f, e.first { it.id == "custom:jump" }.x, 0f)
        for (c in listOf(InputControl.L3, InputControl.R3, InputControl.GUIDE, InputControl.QAM)) assertNotNull(e.firstOrNull { it.id == "legacy:${c.id}" })
    }
}
