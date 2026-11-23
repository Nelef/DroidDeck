package com.droiddeck.launcher.input

import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The gamepad in the band under the game on an unfolded screen (a Galaxy Z Fold: 2184 x 740 under a 16:9 game), laid out as
 * nx2android's band (nxnative's phone layout over the band's height, dm/android/overlay_layout.h): left stick above the d-pad,
 * face buttons above the right stick, shoulders across the band's top corners, View and Menu beside the menu button.
 * Both sides are sticks whatever the Deck tab's trackpads (nx2android's band has no trackpads); a stick is always drawn and
 * starts where the thumb lands.
 * Sizes follow the band's height and are grown so a face button is at least [MIN_FACE_MM] wide on the glass.
 */
internal class FoldBandGeometry(private val width: Int, private val height: Int, private val safe: Rect, private val xdpi: Float) {
    /** ax: the edge x counts from (0 left, 1 right, .5 middle); x: in band heights from it; y: of the band's height; w, h: in band heights. */
    private class Slot(val ax: Float, val x: Float, val y: Float, val w: Float, val h: Float)

    private val w = (width - safe.left - safe.right).toFloat()
    private val h = (height - safe.top - safe.bottom).toFloat()

    val scale: Float by lazy {
        if (w <= 0f || h <= 0f) return@lazy 1f
        val mm = FACE.w * h / xdpi.coerceAtLeast(1f) * 25.4f
        fitScale((MIN_FACE_MM / mm).coerceIn(1f, MAX_GROWTH))
    }

    /** Where nxnative's overlay_layout.h ov_box puts a control: a side's controls scale about its bottom corner, the ones beside the
     * top strip about its top corner, the middle's from the top edge. */
    private fun box(s: Slot): GamepadGeometry.Box {
        val k = scale
        val cx: Float
        val cy: Float
        when {
            s.ax == .5f -> { cx = s.ax * w + s.x * h; cy = s.y * h * k }
            s.y - s.h / 2f < STRIP -> { cx = s.ax * w + s.x * h * k; cy = s.y * h * k }
            else -> { cx = s.ax * w + s.x * h * k; cy = h - (1f - s.y) * h * k }
        }
        return GamepadGeometry.Box(safe.left + cx, safe.top + cy, s.w * h * k, s.h * h * k)
    }

    /** overlay_layout.h ov_fit_scale: the largest scale up to [want] at which the sides stay on the band and apart. */
    private fun fitScale(want: Float): Float {
        var s = want
        val mid = MIDDLE.maxOf { abs(it.x) + it.w / 2f }
        var strip = STRIP
        SIDES.filter { it.y - it.h / 2f < STRIP }.forEach { strip = max(strip, it.y + it.h / 2f) }
        for (c in SIDES) {
            val out = abs(c.x) + c.w / 2f
            if (c.y - c.h / 2f < STRIP) {
                if (c.y + c.h / 2f > 0f) s = min(s, 1f / (c.y + c.h / 2f))
                if (out + mid > 0f) s = min(s, w / h / 2f / (out + mid))
            } else {
                val up = 1f - c.y + c.h / 2f
                if (up + strip > 0f) s = min(s, 1f / (up + strip))
                if (out > 0f) s = min(s, w / h / 2f / out)
            }
        }
        return max(s, 1f)
    }

    /** The menu button's place in view pixels (the band's bar opens from it). */
    fun menuBox(): GamepadGeometry.Box = box(MENU)

    fun place(elements: List<InputSurfaceElement>, dpad: DpadStyle?): List<InputSurfaceElement> {
        if (w <= 0f || h <= 0f) return elements
        val boxes = LinkedHashMap<InputControl, GamepadGeometry.Box>()
        boxes[InputControl.LS] = box(STICK_L)
        boxes[InputControl.RS] = box(STICK_R)
        // Switch positions for Xbox labels: Switch's A is on the right (Xbox B), its B at the bottom (Xbox A), X on top (Y), Y on the left (X)
        boxes[InputControl.B] = box(FACE)
        boxes[InputControl.A] = box(FACE_BOTTOM)
        boxes[InputControl.Y] = box(FACE_TOP)
        boxes[InputControl.X] = box(FACE_LEFT)
        boxes[InputControl.LB] = box(L)
        boxes[InputControl.LT] = box(ZL)
        boxes[InputControl.RB] = box(R)
        boxes[InputControl.RT] = box(ZR)
        boxes[InputControl.SELECT] = box(MINUS)
        boxes[InputControl.START] = box(PLUS)
        boxes[InputControl.L3] = box(L3)
        boxes[InputControl.R3] = box(R3)
        boxes[InputControl.GUIDE] = box(GUIDE)
        boxes[InputControl.QAM] = box(QAM)
        val pad = box(DPAD)
        val radius = min(pad.width, pad.height) / 2f
        val arm = radius * if (dpad == DpadStyle.ARROWS) .25f else .37f
        val inner = if (dpad == DpadStyle.ARROWS) radius * .14f else arm
        val depth = radius - inner
        val distance = (radius + inner) / 2f
        boxes[InputControl.UP] = GamepadGeometry.Box(pad.cx, pad.cy - distance, arm * 2f, depth)
        boxes[InputControl.RIGHT] = GamepadGeometry.Box(pad.cx + distance, pad.cy, depth, arm * 2f)
        boxes[InputControl.DOWN] = GamepadGeometry.Box(pad.cx, pad.cy + distance, arm * 2f, depth)
        boxes[InputControl.LEFT] = GamepadGeometry.Box(pad.cx - distance, pad.cy, depth, arm * 2f)

        val placed = elements.mapNotNull { e ->
            val stock = e.id == "legacy:${e.control.id}"
            when {
                stock && e.control in boxes -> e.at(boxes.getValue(e.control))
                stock -> null   // a control the band leaves out: the rear grips (their own tab), the trackpads
                e.id == KEYBOARD -> e.at(box(KEYBOARD_SLOT))
                else -> e
            }
        }
        val added = boxes.keys.filter { c -> placed.none { it.id == "legacy:${c.id}" } }.map { c ->
            InputSurfaceElement(c, 0f, 0f, 0f, 0f, floating = false, buttonShape = shape(c), showLabel = !c.isTrackpad).at(boxes.getValue(c))
        }
        return placed + added
    }

    private fun shape(c: InputControl) = when (c) {
        InputControl.LB, InputControl.LT, InputControl.RB, InputControl.RT -> InputButtonShape.PILL
        else -> InputButtonShape.CIRCLE
    }

    private val viewW = width.toFloat()
    private val viewH = height.toFloat()

    // a stick floats (it starts where the thumb lands) but is never hidden while idle: DroidDeck's adaptive stick is drawn only while held
    private fun InputSurfaceElement.at(b: GamepadGeometry.Box) =
        copy(x = b.left / viewW, y = b.top / viewH, width = b.width / viewW, height = b.height / viewH,
            floating = kind == InputElementKind.STICK, hideWhenIdle = false).bounded()

    companion object {
        const val MIN_FACE_MM = 10f
        private const val MAX_GROWTH = 1.45f
        private const val KEYBOARD = "builtin:keyboard"

        // nxnative's phone layout (porter android_profile DM_AOV_PHONE)
        private val STICK_L = Slot(0f, .3155f, .6359f, .3883f, .3883f)
        private val STICK_R = Slot(1f, -.7282f, .7816f, .3398f, .3398f)
        private val DPAD = Slot(0f, .7087f, .7913f, .3544f, .3544f)
        private val FACE = Slot(1f, -.2209f, .6505f, .1456f, .1456f)
        private val FACE_BOTTOM = Slot(1f, -.3519f, .7816f, .1456f, .1456f)
        private val FACE_TOP = Slot(1f, -.3519f, .5194f, .1456f, .1456f)
        private val FACE_LEFT = Slot(1f, -.4830f, .6505f, .1456f, .1456f)
        private val L = Slot(0f, .4223f, .3131f, .2621f, .1214f)
        private val ZL = Slot(0f, .1553f, .3131f, .2330f, .1214f)
        private val R = Slot(1f, -.4223f, .3131f, .2621f, .1214f)
        private val ZR = Slot(1f, -.1553f, .3131f, .2330f, .1214f)
        private val MINUS = Slot(0f, .7330f, .0728f, .0971f, .0971f)
        private val PLUS = Slot(1f, -.7330f, .0728f, .0971f, .0971f)
        private val L3 = Slot(0f, .6117f, .4854f, .1068f, .1068f)
        private val R3 = Slot(1f, -.6117f, .4854f, .1068f, .1068f)
        private val MENU = Slot(.5f, 0f, .0485f, .1553f, .0874f)
        // DroidDeck's own: Steam and Quick access either side of the menu button, the keyboard at the bottom middle
        private val GUIDE = Slot(.5f, -.2000f, .0728f, .0971f, .0971f)
        private val QAM = Slot(.5f, .2000f, .0728f, .0971f, .0971f)
        private val KEYBOARD_SLOT = Slot(.5f, 0f, .9000f, .0971f, .0971f)

        private val SIDES = listOf(STICK_L, STICK_R, DPAD, FACE, FACE_BOTTOM, FACE_TOP, FACE_LEFT, L, ZL, R, ZR, MINUS, PLUS, L3, R3)
        private val MIDDLE = listOf(MENU, GUIDE, QAM)
        /** overlay_layout.h ov_strip: the top strip, down to the menu button's bottom edge. */
        private val STRIP = MENU.y + MENU.h / 2f

        /** The band takes this layout: docked under the game on a foldable (not a portrait phone) and wider than tall. */
        fun applies(dock: OnScreenLayout.Dock?, width: Int, height: Int) = dock != null && !dock.portrait && width > height && height > 0
    }
}
