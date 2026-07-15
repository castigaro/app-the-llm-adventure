package com.castigaro.thellmadventure.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.castigaro.thellmadventure.render.IconPainter
import com.castigaro.thellmadventure.world.IconSpec
import kotlin.math.min

/** Kleine View, die ein prozedurales [IconSpec]-Icon malt. */
class IconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    var spec: IconSpec? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Name des Gegenstands — der Painter leitet daraus Alt-Icons ab. */
    var itemName: String = ""
        set(value) {
            field = value
            invalidate()
        }

    /** Goldener Auswahlrahmen (aktiver „Benutze mit …"-Gegenstand). */
    var highlight: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Setzt Icon und Namen in einem Zug (eine Invalidierung genügt). */
    fun bind(spec: IconSpec?, name: String) {
        itemName = name
        this.spec = spec
    }

    override fun onDraw(canvas: Canvas) {
        val s = spec ?: return
        IconPainter.draw(canvas, s, itemName, min(width, height).toFloat(), highlight)
    }
}
