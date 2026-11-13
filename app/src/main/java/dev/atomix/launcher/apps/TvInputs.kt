package dev.atomix.launcher.apps

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

data class InputEntry(val id: String, val label: String)

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

    /**
     * One line for every input the TV lists, CEC devices behind a port included, with all an ordinary app is allowed
     * to read about it. Reads only. It is logged so the question "can an app read a device's name?" can be answered
     * from logcat.
     */
    fun describeAll(): List<String> {
        val tim = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return runCatching { tim.tvInputList }.getOrDefault(emptyList()).map { info ->
            listOf(
                info.id,
                "type=${info.type}",
                "parentId=${info.parentId}",
                "state=${runCatching { tim.getInputState(info.id) }.getOrNull()}",
                "label=${runCatching { info.loadLabel(context) }.getOrNull()}",
                "customLabel=${runCatching { info.loadCustomLabel(context) }.getOrNull()}",
                "passthrough=${info.isPassthroughInput}",
            ).joinToString(" | ")
        }
    }

    fun intentFor(input: InputEntry): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
