package dev.atomix.launcher.livetv.player

import android.media.MediaDrm
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.atomix.launcher.livetv.source.PlayReply
import okhttp3.OkHttpClient

/**
 * Builds the Media3 source for a play reply. [http] adds the Atomix token for the server only; [resolve] turns the
 * server's relative paths into absolute URLs.
 */
@OptIn(UnstableApi::class)
class MediaFactory(http: OkHttpClient, private val resolve: (String) -> String?) {
    private val data = OkHttpDataSource.Factory(http)
    private val drm = DefaultDrmSessionManagerProvider().apply { setDrmHttpDataSourceFactory(data) }

    fun build(reply: PlayReply): MediaSource? = when (reply) {
        is PlayReply.Dash -> resolve(reply.manifest)?.let { url ->
            DashMediaSource.Factory(data).createMediaSource(liveItem(url).build())
        }
        is PlayReply.Drm -> {
            val url = resolve(reply.manifest)
            val licence = resolve(reply.licence)
            if (url == null || licence == null) null
            else DashMediaSource.Factory(data)
                .setDrmSessionManagerProvider(drm)
                .createMediaSource(
                    liveItem(url)
                        .setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(licence).build())
                        .build(),
                )
        }
        is PlayReply.Stream -> resolve(reply.url)?.let { url ->
            if (reply.delivery == "hls") HlsMediaSource.Factory(data).createMediaSource(liveItem(url, HLS_LIVE_OFFSET_MS).build())
            else ProgressiveMediaSource.Factory(data).createMediaSource(MediaItem.fromUri(url))
        }
    }

    /** [targetOffsetMs] null = the manifest's own (DASH: the tuner buffer's suggested delay). */
    private fun liveItem(url: String, targetOffsetMs: Long? = null) =
        MediaItem.Builder().setUri(url).setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder().apply { if (targetOffsetMs != null) setTargetOffsetMs(targetOffsetMs) }.build(),
        )

    companion object {
        /**
         * The server's live remux keeps ~12 s (6 × 2 s pieces) but advertises a 4 s target, so the default start
         * (3 × target) is its oldest piece and playback soon falls behind the window. Start 4 s from the edge.
         */
        const val HLS_LIVE_OFFSET_MS = 4_000L

        fun widevineSupported(): Boolean = runCatching { MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID) }.getOrDefault(false)
    }
}
