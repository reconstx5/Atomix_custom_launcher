package dev.atomix.launcher.livetv.player

import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.LiveClient
import dev.atomix.launcher.livetv.source.PlayCaps
import dev.atomix.launcher.livetv.source.PlayReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Idle : SessionState
    data class Tuning(val channel: Channel) : SessionState
    data class Playing(val channel: Channel, val reply: PlayReply) : SessionState
    data class Failed(val channel: Channel, val message: String) : SessionState
}

/**
 * One viewer's server session for the channel being watched: starts it (replacing the previous one), keeps it alive
 * with a ping every [pingEveryMs], and retries quietly per [retry] when the stream, the ping or the start fails.
 * A failure streak starts at the first failure and ends when the picture starts ([onPlayerReady]) or on a new [tune];
 * a server that answers pings but never sends video still ends in the error card. A stream whose picture hasn't
 * started [firstPictureMs] after the server answered counts as a failure too (the player itself retries quietly
 * for over a minute when the server holds the stream back).
 */
class PlayerSession(
    private val client: LiveClient,
    private val caps: PlayCaps,
    private val scope: CoroutineScope,
    private val pingEveryMs: Long = 30_000,
    private val retry: RetryPolicy = RetryPolicy(),
    private val isWidevineSupported: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
    private val onFailure: (Throwable) -> Unit = {},
    private val firstPictureMs: Long = 20_000,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state

    private var job: Job? = null
    private var pictureDue: Job? = null
    private var current: PlayReply? = null
    private var channel: Channel? = null
    private var attempt = 0
    private var failingSince: Long? = null

    fun tune(channel: Channel) {
        this.channel = channel
        attempt = 0
        failingSince = null
        start(channel, afterMs = 0)
    }

    /** The picture started: that, not a ping the server answers, ends a failure streak. */
    fun onPlayerReady() {
        pictureDue?.cancel()
        attempt = 0
        failingSince = null
    }

    /** The player reported an error it can't recover from by itself. */
    fun onPlayerError() {
        val ch = channel ?: return
        if (_state.value is SessionState.Failed || _state.value is SessionState.Idle) return
        job?.cancel()
        scheduleRetry(ch)
    }

    fun close() {
        pictureDue?.cancel()
        job?.cancel()
        job = null
        channel = null
        _state.value = SessionState.Idle
        stopCurrent()
    }

    private fun start(ch: Channel, afterMs: Long) {
        pictureDue?.cancel()
        job?.cancel()
        _state.value = SessionState.Tuning(ch)
        job = scope.launch {
            if (afterMs > 0) delay(afterMs)
            val reply = try {
                client.play(ch.id, caps, replaces = current?.sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(e)
                scheduleRetry(ch)
                return@launch
            }
            current = reply
            if (reply is PlayReply.Drm && !isWidevineSupported()) {
                _state.value = SessionState.Failed(ch, CANT_PLAY_HERE)
                stopCurrent()
                return@launch
            }
            _state.value = SessionState.Playing(ch, reply)
            pictureDue = scope.launch {
                delay(firstPictureMs)
                onFailure(IllegalStateException("no picture after ${firstPictureMs / 1000} s"))
                onPlayerError()
            }
            keepAlive(ch, reply)
        }
    }

    private suspend fun keepAlive(ch: Channel, reply: PlayReply) {
        while (true) {
            delay(pingEveryMs)
            val alive = try {
                client.ping(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(e)
                false
            }
            if (!alive) {
                scheduleRetry(ch)
                return
            }
        }
    }

    private fun scheduleRetry(ch: Channel) {
        val since = failingSince ?: now().also { failingSince = it }
        val wait = retry.nextDelay(attempt, now() - since)
        if (wait == null) {
            _state.value = SessionState.Failed(ch, UNAVAILABLE)
            stopCurrent()
            return
        }
        attempt++
        start(ch, afterMs = wait)
    }

    private fun stopCurrent() {
        val reply = current ?: return
        current = null
        scope.launch { runCatching { client.stop(reply) } }
    }

    companion object {
        const val UNAVAILABLE = "This channel isn't available right now."
        const val CANT_PLAY_HERE = "This channel can't play on this TV."
    }
}
