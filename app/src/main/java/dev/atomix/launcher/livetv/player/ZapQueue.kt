package dev.atomix.launcher.livetv.player

import dev.atomix.launcher.livetv.source.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Channel up/down. Each [step] returns the channel to show in the banner at once; the actual [tune] waits until
 * presses stop for [delayMs], so flicking through five channels starts one stream, not five.
 */
class ZapQueue(private val scope: CoroutineScope, private val delayMs: Long = 500, private val tune: (Channel) -> Unit) {
    private var pending: Job? = null

    fun step(list: List<Channel>, from: Channel, delta: Int): Channel {
        if (list.isEmpty()) return from
        val n = list.size
        var i = list.indexOfFirst { it.id == from.id }
        if (i < 0) i = if (delta > 0) -1 else n
        val target = list[(i + delta).mod(n)]
        pending?.cancel()
        pending = scope.launch { delay(delayMs); tune(target) }
        return target
    }

    fun cancel() { pending?.cancel(); pending = null }
}
