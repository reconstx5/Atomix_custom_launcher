package dev.atomix.launcher.apps

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

data class InputEntry(val id: String, val label: String)

/** The TV tuner as an input: not an HDMI port, opened as the TV's own Live TV app. */
const val TUNER_INPUT = "tv-tuner"
val TUNER = InputEntry(TUNER_INPUT, "TV tuner")

class TvInputs(private val context: Context) {
    /** Physical HDMI ports only; CEC devices behind a port (parentId != null) are skipped. */
    fun hdmi(): List<InputEntry> {
        val tim = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        val list = runCatching { tim.tvInputList }.getOrDefault(emptyList())
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }
            .map { InputEntry(it.id, it.loadLabel(context).toString()) }
            .sortedBy { it.label }
        Log.i("AtomixInputs", list.joinToString { "${it.label}=${it.id}" })
        return list
    }

    fun intentFor(input: InputEntry): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
