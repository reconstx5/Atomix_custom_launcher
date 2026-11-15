package dev.atomix.launcher.prefs

/**
 * How big the app tiles on Home are: how many fit across, the banner inside a wide tile, the name's size and line,
 * the space between banner and name, and the tile's own top-and-bottom padding (all px at 1920).
 */
enum class TileSize(val label: String, val across: Int, val bannerW: Int, val bannerH: Int, val namePx: Int, val nameLine: Int, val nameGap: Int, val pad: Int) {
    Small("Small", 9, 96, 54, 24, 30, 10, 34),
    Medium("Medium", 7, 112, 63, 28, 34, 10, 34),
    Large("Large", 5, 192, 108, 30, 38, 12, 36),
}

/** A wide tile holds the banner and the name; a full banner fills the tile and has the name under it. */
enum class TileShape(val label: String) { Wide("Wide tile"), FullBanner("Full banner") }

/** The space between app tiles. */
enum class Spacing(val label: String, val gap: Int) { Compact("Compact", 12), Comfortable("Comfortable", 18), Roomy("Roomy", 28) }

/** Phase 2's order of its two rows; read once, to build the rows (phase 3). */
enum class RowOrder(val label: String) { AppsFirst("Apps, then On now"), OnNowFirst("On now, then Apps") }

/** Every "On now" layout shows this many rows of small cards at a time. */
const val SMALL_ROWS = 2

/** How "On now" is arranged: with or without a large card, and how many small cards sit side by side. */
enum class OnNowLayout(val label: String, val large: Boolean, val columns: Int) {
    LargeTwo("One large, two small", true, 1),
    LargeFour("One large, four small", true, 2),
    SixSmall("Six small", false, 3),
}

/** How Home is laid out. The defaults are the Home of the approved board. */
data class HomeLayout(
    val tileSize: TileSize = TileSize.Medium,
    val tileShape: TileShape = TileShape.Wide,
    val spacing: Spacing = Spacing.Comfortable,
    val appNames: Boolean = true,
    val onNow: OnNowLayout = OnNowLayout.LargeTwo,
)
