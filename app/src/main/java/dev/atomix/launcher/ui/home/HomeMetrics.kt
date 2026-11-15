package dev.atomix.launcher.ui.home

import dev.atomix.launcher.prefs.HomeLayout
import dev.atomix.launcher.prefs.OnNowLayout
import dev.atomix.launcher.prefs.RowOrder
import dev.atomix.launcher.prefs.TileShape
import dev.atomix.launcher.ui.kit.textRoom
import kotlin.math.min

/** The space between "On now" cards, across and down. */
const val CARD_GAP = 18

/** The least height the "On now" cards area may have, whatever is chosen in Settings › Home layout. */
const val MIN_CARDS_H = 370

/** The page between its 100 px margins, and where its last row ends (as on the Home board). */
private const val CONTENT_W = 1720
private const val FIRST_HEADING_Y = 126
private const val FIRST_ROW_Y = 208
private const val LAST_ROW_END = 969

/** A heading sits 84 above its row; a row's heading starts 30 under the row before it. */
private const val HEADING_TO_ROW = 84
private const val ROW_TO_HEADING = 30

/** With "On now" first, the cards are followed by a band for the "more channels" line before the next heading. */
private const val MORE_BAND = 64

/**
 * Where everything on Home goes and how big it is, px at 1920: Home and the preview in Settings both draw from this,
 * so they cannot disagree. Positions depend on the layout alone, never on how many apps or channels there are.
 *
 * [tileH] is the focusable tile; [rowH] also holds a name drawn under it ([nameUnder]). [largeW] is 0 when the layout
 * has no large card. [smallX] is from the left edge of the cards area. [moreY] is where the "more channels" line goes.
 */
data class HomeMetrics(
    val tileW: Int,
    val tileH: Int,
    val rowH: Int,
    val bannerW: Int,
    val bannerH: Int,
    val gap: Int,
    val namePx: Int,
    val nameLine: Int,
    val nameGap: Int,
    val showNames: Boolean,
    val nameUnder: Boolean,
    val appsHeadingY: Int,
    val appsY: Int,
    val onNowHeadingY: Int,
    val cardsY: Int,
    val cardsH: Int,
    val moreY: Int,
    val largeW: Int,
    val smallX: Int,
    val smallW: Int,
    val smallH: Int,
    val columns: Int,
)

fun homeMetrics(layout: HomeLayout): HomeMetrics {
    val size = layout.tileSize
    val gap = layout.spacing.gap
    val tileW = (CONTENT_W - (size.across - 1) * gap) / size.across
    val full = layout.tileShape == TileShape.FullBanner
    val tileH = if (full) tileW * 9 / 16 else size.bannerH + (if (layout.appNames) size.nameGap + size.nameLine else 0) + size.pad
    val rowH = if (full && layout.appNames) tileH + 10 + size.nameLine else tileH

    val appsHeadingY: Int
    val appsY: Int
    val onNowHeadingY: Int
    val cardsY: Int
    val cardsH: Int
    if (layout.rowOrder == RowOrder.AppsFirst) {
        appsHeadingY = FIRST_HEADING_Y
        appsY = FIRST_ROW_Y
        onNowHeadingY = appsY + rowH + ROW_TO_HEADING
        cardsY = onNowHeadingY + HEADING_TO_ROW
        cardsH = LAST_ROW_END - cardsY
    } else {
        onNowHeadingY = FIRST_HEADING_Y
        cardsY = FIRST_ROW_Y
        appsY = LAST_ROW_END - rowH
        appsHeadingY = appsY - HEADING_TO_ROW
        cardsH = appsHeadingY - MORE_BAND - cardsY
    }

    val largeW: Int
    val smallX: Int
    val smallW: Int
    when (layout.onNow) {
        OnNowLayout.LargeTwo -> { largeW = 1050; smallX = 1074; smallW = 646 }
        OnNowLayout.LargeFour -> { largeW = 812; smallX = 836; smallW = (CONTENT_W - 836 - CARD_GAP) / 2 }
        OnNowLayout.SixSmall -> { largeW = 0; smallX = 0; smallW = (CONTENT_W - 2 * CARD_GAP) / 3 }
    }
    return HomeMetrics(
        tileW = tileW, tileH = tileH, rowH = rowH,
        bannerW = if (full) tileW else size.bannerW, bannerH = if (full) tileH else size.bannerH,
        gap = gap, namePx = size.namePx, nameLine = size.nameLine, nameGap = size.nameGap,
        showNames = layout.appNames, nameUnder = full,
        appsHeadingY = appsHeadingY, appsY = appsY, onNowHeadingY = onNowHeadingY, cardsY = cardsY, cardsH = cardsH,
        moreY = cardsY + cardsH + 16,
        largeW = largeW, smallX = smallX, smallW = smallW, smallH = (cardsH - CARD_GAP) / 2,
        columns = layout.onNow.columns,
    )
}

/** How wide the artwork at the right of a small card is: 370 on the board's card, less on a narrower one. */
fun smallArtWidth(cardW: Int): Int = min(370, cardW - 276)

/**
 * How wide the words of a small card may run. Beside artwork they have 330, as on the board's card, and as much more
 * as Large text needs: on a narrower card it is the artwork that gives way ([smallArtWidth]), never the words. With
 * no artwork there is nothing to stay clear of, so they take the card's width less its 30 px margins.
 */
fun smallCardTextRoom(cardW: Int, hasArt: Boolean, textScale: Float): Int =
    if (hasArt) textRoom(330, textScale) else cardW - 60

/**
 * The fade of a small card's own colour over its artwork, as three stops across the card's width: solid up to 2 px
 * into the artwork, 55 % at 123 px further, clear at 252 px further. On the board's 646-wide card these are 0.43,
 * 0.62 and 0.82; a narrower card keeps the same distances, so its words sit on the same strength of colour.
 */
fun smallArtFade(cardW: Int): List<Float> {
    if (cardW == 646) return listOf(0.43f, 0.62f, 0.82f)
    val start = cardW - smallArtWidth(cardW) + 2
    return listOf(start, start + 123, start + 252).map { (it / cardW.toFloat()).coerceAtMost(1f) }
}

/** How a small card's words are fitted to its height: where the channel's name and the lines start, and whether "Next:" shows. */
data class SmallCardFit(val chipTop: Int, val textTop: Int, val showNext: Boolean)

/**
 * A small card is 244 high on the board. Bigger app tiles or "On now" first make it shorter; then the lines move up
 * 16, and if that is not enough the "Next:" line goes. The title, the time and the bar always show. Lines are 46 and
 * 34 high and grow with the text size; the bar and its space take 29.
 */
fun smallCardFit(heightPx: Int, textScale: Float): SmallCardFit {
    val withNext = (46 + 34 + 34) * textScale + 29
    val without = (46 + 34) * textScale + 29
    return when {
        82 + withNext <= heightPx -> SmallCardFit(22, 82, true)
        66 + withNext <= heightPx -> SmallCardFit(14, 66, true)
        82 + without <= heightPx -> SmallCardFit(22, 82, false)
        else -> SmallCardFit(14, 66, false)
    }
}

/**
 * How many lines the large card's title may take: two while they clear the channel's name at the top (78), one when
 * the card is at its shortest. A title line is 74 and a detail line 42, growing with the text size; the bar and the
 * card's margins take 68.
 */
fun largeTitleLines(heightPx: Int, textScale: Float): Int = if (78 + (2 * 74 + 2 * 42) * textScale + 68 <= heightPx) 2 else 1

/**
 * Whether the large card's "OK Watch" chip goes top right instead of bottom right. The words and the progress bar run
 * up to 720 from the card's 36 px margin; the chip is about 246 wide with its margin, and needs 16 clear of them.
 */
fun watchHintOnTop(cardW: Int): Boolean = cardW < 36 + 720 + 16 + 246

/** The two rows of Home. */
enum class HomeRow { Apps, OnNow }

/** Home's rows from top to bottom, as chosen in Settings › Home layout › Row order. */
fun rowsInOrder(order: RowOrder): List<HomeRow> =
    if (order == RowOrder.AppsFirst) listOf(HomeRow.Apps, HomeRow.OnNow) else listOf(HomeRow.OnNow, HomeRow.Apps)

/** The row over [row], or null when it is the first: Up from it then goes to the top bar. */
fun rowAbove(row: HomeRow, order: RowOrder): HomeRow? = rowsInOrder(order).let { it.getOrNull(it.indexOf(row) - 1) }

/** The row under [row], or null when it is the last: Down from it then goes nowhere. */
fun rowBelow(row: HomeRow, order: RowOrder): HomeRow? = rowsInOrder(order).let { it.getOrNull(it.indexOf(row) + 1) }
