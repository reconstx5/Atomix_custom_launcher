package dev.atomix.launcher.ui.kit

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A width that holds a fixed set of words, at the chosen text size: [px] as drawn for Standard text, grown by the
 * same amount as the text ([textScale] is 1 for Standard, 1.15 for Large), so words that fit before still fit.
 */
fun textRoom(px: Int, textScale: Float): Int = px * (textScale * 100).roundToInt() / 100

/**
 * How tall a menu panel is with [items] rows: 20 of padding, a 36-high title line, 72-high items 10 apart from the
 * title and from each other, 20 of padding.
 */
fun menuHeight(items: Int): Int = 20 + 36 + items * 82 + 20

/**
 * Where a menu's top goes, in a page's content box (770 high, y 210 to the hint bar): 24 above the top of the thing
 * it belongs to, but never above the page and never closer than 16 to the hint bar ([floor] is 770 - 16).
 */
fun menuTop(anchorTop: Int, height: Int, floor: Int = 754): Int = (anchorTop - 24).coerceIn(0, max(floor - height, 0))
