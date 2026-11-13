package dev.atomix.launcher.livetv.player

import dev.atomix.launcher.livetv.source.Channel

/** Which channels Channel up/down steps through: the pinned ones (Home) or every channel (Live TV tab). */
enum class ZapSource { Pinned, All }

/** The channels to zap through; with nothing pinned, Pinned falls back to every channel. */
fun zapList(source: ZapSource, pinned: List<Channel>, all: List<Channel>): List<Channel> =
    if (source == ZapSource.Pinned && pinned.isNotEmpty()) pinned else all
