package dev.atomix.launcher.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.atomix.launcher.prefs.HomeLayout
import dev.atomix.launcher.ui.home.CARD_GAP
import dev.atomix.launcher.ui.home.homeMetrics
import dev.atomix.launcher.ui.kit.Wash
import dev.atomix.launcher.ui.kit.WashKind
import dev.atomix.launcher.ui.theme.paletteFor
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.ringArgb

enum class BlockKind { Tile, LargeCard, SmallCard }

/** One block of the preview, where Home draws the real thing (px at 1920). */
data class PreviewBlock(val kind: BlockKind, val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * The blocks Home would show with [layout]: as many app tiles as fit across, and every card slot of the "On now"
 * layout filled. It comes from the same [homeMetrics] Home is drawn from, so the preview cannot disagree with Home.
 */
fun previewBlocks(layout: HomeLayout): List<PreviewBlock> {
    val m = homeMetrics(layout)
    val blocks = ArrayList<PreviewBlock>()
    repeat(layout.tileSize.across) { i -> blocks += PreviewBlock(BlockKind.Tile, 100 + i * (m.tileW + m.gap), m.appsY, m.tileW, m.tileH) }
    if (m.largeW > 0) blocks += PreviewBlock(BlockKind.LargeCard, 100, m.cardsY, m.largeW, m.cardsH)
    for (row in 0 until 2) for (column in 0 until m.columns) {
        blocks += PreviewBlock(BlockKind.SmallCard, 100 + m.smallX + column * (m.smallW + CARD_GAP), m.cardsY + row * (m.smallH + CARD_GAP), m.smallW, m.smallH)
    }
    return blocks
}

/** What the preview picks out, so the eye goes to what the focused row changes. */
enum class PreviewMark { None, Tiles, Cards, FocusedCard }

fun previewMark(item: SettingsItem?): PreviewMark = when (item) {
    // These change the ring, its glow or the colours around it: the first card is shown with focus.
    SettingsItem.Theme, SettingsItem.Accent, SettingsItem.FocusHighlight, SettingsItem.ReducedEffects -> PreviewMark.FocusedCard
    SettingsItem.TileSize, SettingsItem.TileShape, SettingsItem.Spacing, SettingsItem.AppNames -> PreviewMark.Tiles
    else -> PreviewMark.None
}

/** Which sections draw the preview beside their rows. */
fun hasPreview(section: SettingsSection): Boolean = section == SettingsSection.Appearance || section == SettingsSection.HomeLayout

/**
 * What the preview draws: with a list of choices [open] it is the choice that has focus in it ([focused]), before OK;
 * otherwise what is chosen [now].
 */
fun previewChoice(now: Chosen, open: SettingsItem?, focused: Int?): Chosen =
    if (open != null && focused != null) withChoice(open, focused, now) else now

/** The preview is the Home page at this fraction of its size. */
private const val PREVIEW_W = 450
private const val PREVIEW_H = 253
private const val SCALE = PREVIEW_W / 1920f

/**
 * A small picture of Home (450 × 253) under the label "PREVIEW", in the theme, accent and layout of [shown]. Blocks
 * stand for apps and channels. It reads nothing but [shown], so it can show a choice before it is made, and it never
 * takes focus. [glow] is whether the focus glow would show.
 */
@Composable
fun HomePreview(shown: Chosen, mark: PreviewMark, glow: Boolean, modifier: Modifier = Modifier) {
    val palette = paletteFor(shown.look.theme)
    val accent = Color(shown.look.accent.argb)
    val ring = Color(ringArgb(shown.look.accent))
    val blocks = previewBlocks(shown.layout)
    val shape = RoundedCornerShape(px(18))
    Column(modifier.width(px(PREVIEW_W)), verticalArrangement = Arrangement.spacedBy(px(12))) {
        GroupLabel("PREVIEW")
        Box(Modifier.size(px(PREVIEW_W), px(PREVIEW_H)).clip(shape).border(px(1), Color.White.copy(alpha = 0.10f), shape)) {
            Wash(WashKind.Ground, theme = shown.look.theme)
            Box(
                Modifier.fillMaxSize().drawBehind {
                    // One unit here is one px of the 450-wide picture.
                    val u = size.width / PREVIEW_W
                    fun bar(x: Float, y: Float, w: Float, h: Float, colour: Color, r: Float = h / 2) =
                        drawRoundRect(colour, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u))
                    // The top bar, as the board draws it: wordmark, three tabs with the accent under the open one, a round button.
                    bar(22f, 16f, 62f, 6f, Color.White)
                    bar(196f, 16f, 22f, 6f, Color.White)
                    bar(196f, 26f, 22f, 3f, accent)
                    bar(230f, 16f, 28f, 6f, Color(palette.label).copy(alpha = 0.7f))
                    bar(270f, 16f, 20f, 6f, Color(palette.label).copy(alpha = 0.7f))
                    bar(414f, 12f, 14f, 14f, Color(palette.keyCap))

                    val firstCard = blocks.firstOrNull { it.kind != BlockKind.Tile }
                    blocks.forEach { b ->
                        val x = b.x * SCALE
                        val y = b.y * SCALE
                        val w = b.w * SCALE
                        val h = b.h * SCALE
                        val isTile = b.kind == BlockKind.Tile
                        val marked = (mark == PreviewMark.Tiles && isTile) || (mark == PreviewMark.Cards && !isTile) || (mark == PreviewMark.FocusedCard && b === firstCard)
                        val corner = if (isTile) 5f else 6f
                        if (marked && mark == PreviewMark.FocusedCard && glow) outline(x, y, w, h, corner, u, accent.copy(alpha = 0.30f), grow = 4f, width = 4f)
                        // Tiles and the large card stand a shade lighter than the small cards, as on the boards.
                        bar(x, y, w, h, Color(if (marked || b.kind != BlockKind.SmallCard) palette.raised else palette.tile), corner)
                        if (marked) outline(x, y, w, h, corner, u, ring, grow = 1f, width = 2f)
                        if (b.kind == BlockKind.LargeCard && h > 44f) {
                            // A title, and a programme's progress in the accent.
                            bar(x + 12f, y + h - 32f, minOf(110f, w - 24f), 7f, Color.White)
                            bar(x + 12f, y + h - 16f, minOf(150f, w - 24f), 4f, Color.White.copy(alpha = 0.26f))
                            bar(x + 12f, y + h - 16f, minOf(60f, w - 24f), 4f, accent)
                        }
                    }
                },
            )
        }
    }
}

/** A rounded outline [grow] outside a block, [width] thick (all in preview px; [u] turns them into screen px). */
private fun DrawScope.outline(x: Float, y: Float, w: Float, h: Float, corner: Float, u: Float, colour: Color, grow: Float, width: Float) {
    drawRoundRect(
        colour,
        Offset((x - grow) * u, (y - grow) * u), Size((w + 2 * grow) * u, (h + 2 * grow) * u),
        CornerRadius((corner + grow) * u), style = Stroke(width * u),
    )
}
