package dev.atomix.launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.atomix.launcher.data.Item
import dev.atomix.launcher.data.minutesLeft
import dev.atomix.launcher.data.progressFraction
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.GlassButton
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp

/** Backdrop crossfade (spec: 400 ms). Coil skips it when the art is already in memory (prefetched neighbours). */
const val HERO_FADE_MS = 400

/** Height of the hero text block (design px): logo or 2-line title, meta, progress, 2-line overview, buttons. */
const val HERO_INFO_H = 520

fun accentOf(item: Item?): Color = parseTint(item?.tintWide) ?: parseTint(item?.tint) ?: AtomixColors.Ice

/** Full-bleed artwork; Orbit's page shade is baked into the bitmap by [OrbitScrim], so this draws one layer. */
@Composable
fun HeroBackdrop(item: Item?, offline: Boolean, imageUrl: (String?) -> String?, loader: ImageLoader) {
    val context = LocalContext.current
    val url = if (offline) null else (imageUrl(item?.backdrop) ?: imageUrl(item?.poster))
    if (url != null) {
        AsyncImage(
            model = heroRequest(context, url, HERO_FADE_MS),
            imageLoader = loader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else if (offline || item == null) {
        OfflineBackdrop()
    }
}

/** Calm Atomix-branded artwork for when the server can't be reached (drawn, so no bitmap in memory). */
@Composable
fun OfflineBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF1B2A4A), Color(0xFF0B0B16), AtomixColors.DeepSpace),
                center = Offset(size.width * 0.72f, size.height * 0.38f),
                radius = size.width * 0.55f,
            ),
        )
        val c = Offset(size.width * 0.72f, size.height * 0.38f)
        val r = size.height * 0.30f
        val ring = AtomixColors.Ice.copy(alpha = 0.16f)
        for (angle in listOf(0f, 60f, 120f)) rotate(angle, c) {
            drawOval(ring, Offset(c.x - r, c.y - r * 0.38f), Size(r * 2, r * 0.76f), style = Stroke(size.height * 0.004f))
        }
        drawCircle(AtomixColors.Ice.copy(alpha = 0.22f), r * 0.09f, c)
    }
}

fun metaLine(item: Item): String = listOfNotNull(
    item.year?.toString(),
    item.genres.take(2).joinToString(" · ").ifEmpty { null },
    (item.runtime ?: item.duration?.let { (it / 60).toInt() })?.takeIf { it > 0 }?.let { m -> if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" },
    item.season?.let { s -> item.episode?.let { "S$s · E$it" } },
).joinToString(" · ")

@Composable
fun HeroInfo(
    item: Item?,
    offline: Boolean,
    imageUrl: (String?) -> String?,
    loader: ImageLoader,
    playFocus: FocusRequester,
    onPlay: (Item) -> Unit,
    onInfo: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Fixed height, content pinned to the bottom: the row below never moves when the hero's text changes,
    // so a focus move re-lays out only this block.
    Column(modifier.width(px(1100)).height(px(HERO_INFO_H)), verticalArrangement = Arrangement.spacedBy(px(18), Alignment.Bottom)) {
        if (item == null) {
            Box(Modifier.height(px(200)))
            BasicText(
                if (offline) "Atomix is offline" else "Loading Atomix…",
                style = AtomixType.caption(),
            )
            return@Column
        }
        val logo = imageUrl(item.logo)
        var logoFailed by remember(logo) { mutableStateOf(logo == null) }
        Box(Modifier.heightIn(min = px(140)), contentAlignment = Alignment.BottomStart) {
            if (!logoFailed) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(logo).size(640, 200).build(),
                    imageLoader = loader,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    onError = { logoFailed = true },
                    modifier = Modifier.widthIn(max = px(640)).height(px(200)),
                )
            } else {
                BasicText(item.title, style = AtomixType.display().copy(fontSize = pxSp(76), lineHeight = pxSp(84)), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        BasicText(metaLine(item), style = AtomixType.label().copy(color = AtomixColors.TextSoft))
        val fraction = progressFraction(item.progress, item.duration)
        val left = minutesLeft(item.progress, item.duration)
        if (fraction != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(18))) {
                Box(Modifier.width(px(360)).height(px(5)).background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.02f, 1f)).background(accentOf(item), RoundedCornerShape(50)))
                }
                if (left != null) BasicText("$left min left", style = AtomixType.caption().copy(color = AtomixColors.TextSoft))
            }
        }
        item.overview?.takeIf { it.isNotBlank() }?.let {
            BasicText(it, style = AtomixType.body(), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(px(860)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(px(20)), modifier = Modifier.padding(top = px(8))) {
            GlassButton(
                if (left != null) "Resume · $left min left" else "Play",
                onClick = { onPlay(item) },
                primary = true,
                leading = "▶",
                modifier = Modifier.focusRequester(playFocus),
            )
            GlassButton("More info", onClick = { onInfo(item) })
        }
    }
}
