package dev.atomix.launcher.livetv.player

/** Player overlays, highest first: Back closes them in this order. */
enum class Layer { NumberEntry, ChannelList, Controls, Banner }

/** Which overlays are open. Back closes the top one; when none is open, Back leaves playback. */
class Layers {
    private val open = mutableSetOf<Layer>()

    fun show(l: Layer) { open += l }
    fun hide(l: Layer) { open -= l }
    fun isOpen(l: Layer) = l in open
    fun top(): Layer? = Layer.entries.firstOrNull { it in open }

    /** Closes the top layer; false when nothing was open. */
    fun back(): Boolean {
        val t = top() ?: return false
        open -= t
        return true
    }
}
