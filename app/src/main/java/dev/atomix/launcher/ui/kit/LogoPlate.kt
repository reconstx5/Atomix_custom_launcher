package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/**
 * A channel's logo on the one light plate every logo sits on, fitted within 78 % of each side. With no logo, or one
 * that fails to load, the plate carries the channel's name instead of standing empty.
 */
@Composable
fun LogoPlate(
    channel: Channel,
    logoUrl: String?,
    loader: ImageLoader,
    modifier: Modifier = Modifier,
    widthPx: Int = 108,
    heightPx: Int = 60,
    cornerPx: Int = 12,
) {
    var failed by remember(logoUrl) { mutableStateOf(false) }
    val context = LocalContext.current
    Box(
        modifier.size(px(widthPx), px(heightPx)).background(AtomixColors.LogoPlate, RoundedCornerShape(px(cornerPx))),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl != null && !failed) {
            val request = remember(logoUrl) {
                ImageRequest.Builder(context).data(logoUrl).size(320, 160).listener(onError = { _, _ -> failed = true }).build()
            }
            AsyncImage(
                request, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(px(widthPx * 0.78f), px(heightPx * 0.78f)),
            )
        } else {
            BasicText(
                channel.name,
                Modifier.padding(horizontal = px(6)),
                style = AtomixType.note().copy(fontWeight = FontWeight.Bold, color = AtomixColors.Ink, textAlign = TextAlign.Center),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
