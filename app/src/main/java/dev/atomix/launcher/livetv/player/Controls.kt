package dev.atomix.launcher.livetv.player

import dev.atomix.launcher.livetv.source.PlayReply

/** Pause and ±30 s only make sense on the tuner's rewindable DASH buffer once it holds at least a minute. */
fun controlsSupported(reply: PlayReply, liveWindowMs: Long?): Boolean =
    reply is PlayReply.Dash && reply.rewind && (liveWindowMs ?: 0) >= MIN_WINDOW_MS

private const val MIN_WINDOW_MS = 60_000L
