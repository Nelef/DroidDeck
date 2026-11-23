package com.droiddeck.launcher.input

import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The gamepad in the band under the game on an unfolded screen (a Galaxy Z Fold: 2184 x 740 under a 16:9 game), laid out as
 * nx2android's band (nxnative's layouts over the band's height, dm/android/overlay_layout.h): left stick above the d-pad,
 * face buttons above the right stick, shoulders across the band's top corners, View and Menu beside the menu button,
 * the keyboard button at the bottom middle. A band at least [PHONE_ASPECT] times as wide as tall takes the phone's layout,
 * a taller one (the player dragged the game smaller) the tablet's.
 * Both sides are sticks whatever the Deck tab's trackpads (nx2android's band has no trackpads); a stick is always drawn and
 * starts where the thumb lands.
 * Sizes follow the band's height and are grown so a face button is at least [MIN_FACE_MM] wide on the glass.
 */
internal class FoldBandGeometry(private val width: Int, private val height: Int, private val safe: Rect, private val xdpi: Float) {
    /** ax: the edge x counts from (0 left, 1 right, .5 middle); x: in band heights from it; y: of the band's height; w, h: in band
     * heights. A middle slot counts y from the top edge, or from the bottom one when [bottom]. */
    private class Slot(val ax: Float, val x: Float, val y: Float, val w: Float, val h: Float, val bottom: Boolean = false)

    private class Layout(
        val stickL: Slot, val stickR: Slot, val dpad: Slot,
        /** Switch's A (right), B (bottom), X (top) and Y (left) */
        val faceA: Slot, val faceB: Slot, val faceX: Slot, val faceY: Slot,
        val l: Slot, val zl: Slot, val r: Slot, val zr: Slot, val minus: Slot, val plus: Slot, val l3: Slot, val r3: Slot,
        val menu: Slot, val guide: Slot, val qam: Slot, val keyboard: Slot,
    ) {
        val sides = listOf(stickL, stickR, dpad, faceA, faceB, faceX, faceY, l, zl, r, zr, minus, plus, l3, r3)
        /** the top middle (the keyboard at the bottom middle is not part of it) */
        val middle = listOf(menu, guide, qam)
        /** overlay_layout.h ov_strip: the top strip, down to the menu button's bottom edge. */
        val strip = menu.y + menu.h / 2f
    }

    private val w = (width - safe.left - safe.right).toFloat()
    private val h = (height - safe.top - safe.bottom).toFloat()
    private val layout = if (h > 0f && w / h >= PHONE_ASPECT) PHONE else TABLET

    val scale: Float by lazy {
        if (w <= 0f || h <= 0f) return@lazy 1f
        val mm = layout.faceA.w * h / xdpi.coerceAtLeast(1f) * 25.4f
        fitScale((MIN_FACE_MM / mm).coerceIn(1f, MAX_GROWTH))
    }

    /** Where nxnative's overlay_layout.h ov_box puts a control: a side's controls scale about its bottom corner, the ones beside the
     * top strip about its top corner, the middle's from the top edge (the keyboard's from the bottom edge). */
    private fun box(s: Slot): GamepadGeometry.Box {
        val k = scale
        val cx: Float
        val cy: Float
        when {
            s.ax == .5f -> { cx = s.ax * w + s.x * h; cy = if (s.bottom) h - (1f - s.y) * h * k else s.y * h * k }
            s.y - s.h / 2f < layout.strip -> { cx = s.ax * w + s.x * h * k; cy = s.y * h * k }
            else -> { cx = s.ax * w + s.x * h * k; cy = h - (1f - s.y) * h * k }
        }
        return GamepadGeometry.Box(safe.left + cx, safe.top + cy, s.w * h * k, s.h * h * k)
    }

    /** overlay_layout.h ov_fit_scale: the largest scale up to [want] at which the sides stay on the band and apart. */
    private fun fitScale(want: Float): Float {
        var s = want
        val top = layout.strip
        val mid = layout.middle.maxOf { abs(it.x) + it.w / 2f }
        var strip = top
        layout.sides.filter { it.y - it.h / 2f < top }.forEach { strip = max(strip, it.y + it.h / 2f) }
        for (c in layout.sides) {
            val out = abs(c.x) + c.w / 2f
            if (c.y - c.h / 2f < top) {
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
    fun menuBox(): GamepadGeometry.Box = box(layout.menu)

    fun place(elements: List<InputSurfaceElement>, dpad: DpadStyle?): List<InputSurfaceElement> {
        if (w <= 0f || h <= 0f) return elements
        val L = layout
        val boxes = LinkedHashMap<InputControl, GamepadGeometry.Box>()
        boxes[InputControl.LS] = box(L.stickL)
        boxes[InputControl.RS] = box(L.stickR)
        // Switch positions for Xbox labels: Switch's A is on the right (Xbox B), its B at the bottom (Xbox A), X on top (Y), Y on the left (X)
        boxes[InputControl.B] = box(L.faceA)
        boxes[InputControl.A] = box(L.faceB)
        boxes[InputControl.Y] = box(L.faceX)
        boxes[InputControl.X] = box(L.faceY)
        boxes[InputControl.LB] = box(L.l)
        boxes[InputControl.LT] = box(L.zl)
        boxes[InputControl.RB] = box(L.r)
        boxes[InputControl.RT] = box(L.zr)
        boxes[InputControl.SELECT] = box(L.minus)
        boxes[InputControl.START] = box(L.plus)
        boxes[InputControl.L3] = box(L.l3)
        boxes[InputControl.R3] = box(L.r3)
        boxes[InputControl.GUIDE] = box(L.guide)
        boxes[InputControl.QAM] = box(L.qam)
        val pad = box(L.dpad)
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
                e.id == KEYBOARD -> e.at(box(L.keyboard))
                else -> e
            }
        }
        val added = boxes.keys.filter { c -> placed.none { it.id == "legacy:${c.id}" } }.map { c ->
            InputSurfaceElement(c, 0f, 0f, 0f, 0f, floating = false, buttonShape = shape(c), showLabel = !c.isTrackpad).at(boxes.getValue(c))
        }
        // the keyboard button is always there: DroidDeck's docked layouts drop the player's one
        val keyboard = if (placed.any { it.id == KEYBOARD }) emptyList() else listOf(InputSurfaceElement(InputControl.A, 0f, 0f, 0f, 0f, KEYBOARD,
            name = "Keyboard", icon = InputButtonIcons.KEYBOARD, buttonShape = InputButtonShape.CIRCLE, floating = false).at(box(L.keyboard)))
        return placed + added + keyboard
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
        /** overlay_layout.h OV_PHONE_ASPECT: a band this many times as wide as tall takes the phone's layout */
        const val PHONE_ASPECT = 1.9f
        private const val MAX_GROWTH = 1.45f
        private const val KEYBOARD = "builtin:keyboard"

        // nxnative's phone layout (porter android_profile DM_AOV_PHONE); Steam, Quick access and the keyboard are DroidDeck's own
        private val PHONE = Layout(
            stickL = Slot(0f, .3155f, .6359f, .3883f, .3883f), stickR = Slot(1f, -.7282f, .7816f, .3398f, .3398f),
            dpad = Slot(0f, .7087f, .7913f, .3544f, .3544f),
            faceA = Slot(1f, -.2209f, .6505f, .1456f, .1456f), faceB = Slot(1f, -.3519f, .7816f, .1456f, .1456f),
            faceX = Slot(1f, -.3519f, .5194f, .1456f, .1456f), faceY = Slot(1f, -.4830f, .6505f, .1456f, .1456f),
            l = Slot(0f, .4223f, .3131f, .2621f, .1214f), zl = Slot(0f, .1553f, .3131f, .2330f, .1214f),
            r = Slot(1f, -.4223f, .3131f, .2621f, .1214f), zr = Slot(1f, -.1553f, .3131f, .2330f, .1214f),
            minus = Slot(0f, .7330f, .0728f, .0971f, .0971f), plus = Slot(1f, -.7330f, .0728f, .0971f, .0971f),
            l3 = Slot(0f, .6117f, .4854f, .1068f, .1068f), r3 = Slot(1f, -.6117f, .4854f, .1068f, .1068f),
            menu = Slot(.5f, 0f, .0485f, .1553f, .0874f),
            guide = Slot(.5f, -.2000f, .0728f, .0971f, .0971f), qam = Slot(.5f, .2000f, .0728f, .0971f, .0971f),
            keyboard = Slot(.5f, 0f, .9000f, .0971f, .0971f, bottom = true),
        )

        // nxnative's tablet layout (DM_AOV_TABLET)
        private val TABLET = Layout(
            stickL = Slot(0f, .2105f, .5789f, .2421f, .2421f), stickR = Slot(1f, -.4342f, .7632f, .2211f, .2211f),
            dpad = Slot(0f, .4342f, .7632f, .2263f, .2263f),
            faceA = Slot(1f, -.1421f, .5658f, .0895f, .0895f), faceB = Slot(1f, -.2237f, .6474f, .0895f, .0895f),
            faceX = Slot(1f, -.2237f, .4842f, .0895f, .0895f), faceY = Slot(1f, -.3053f, .5658f, .0895f, .0895f),
            l = Slot(0f, .2737f, .3658f, .1684f, .0737f), zl = Slot(0f, .1053f, .3658f, .1474f, .0737f),
            r = Slot(1f, -.2737f, .3658f, .1684f, .0737f), zr = Slot(1f, -.1053f, .3658f, .1474f, .0737f),
            minus = Slot(0f, .0842f, .2632f, .0632f, .0632f), plus = Slot(1f, -.0842f, .2632f, .0632f, .0632f),
            l3 = Slot(0f, .3974f, .5079f, .0684f, .0684f), r3 = Slot(1f, -.3974f, .5079f, .0684f, .0684f),
            menu = Slot(.5f, 0f, .0368f, .1000f, .0579f),
            guide = Slot(.5f, -.1300f, .0368f, .0632f, .0632f), qam = Slot(.5f, .1300f, .0368f, .0632f, .0632f),
            keyboard = Slot(.5f, 0f, .9300f, .0632f, .0632f, bottom = true),
        )

        /** The band takes this layout: docked under the game on a foldable (not a portrait phone) and wider than tall. */
        fun applies(dock: OnScreenLayout.Dock?, width: Int, height: Int) = dock != null && !dock.portrait && width > height && height > 0
    }
}
