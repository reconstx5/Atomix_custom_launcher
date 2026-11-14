package dev.atomix.launcher.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import dev.atomix.launcher.prefs.AccentId
import dev.atomix.launcher.prefs.focusHighlightRow
import dev.atomix.launcher.ui.kit.RowEnding
import dev.atomix.launcher.ui.kit.SettingRow
import dev.atomix.launcher.ui.theme.px

/**
 * Settings › Appearance, as on its board: the rows of [groupsOf] under their labels. A change shows at once; nothing
 * here is applied later. [openList] is the row whose list of choices is open, which keeps a quiet outline meanwhile.
 * [onStep] is Left or Right on a row that steps in place (the choice's new index); [onOpenList] is OK on a row whose
 * choices open as a list.
 */
@Composable
fun AppearancePane(
    host: PaneHost,
    now: Chosen,
    reduced: Boolean,
    /** The TV's own clock format, which the Clock row shows as chosen until the owner chooses. */
    tvIs24: Boolean,
    openList: SettingsItem?,
    onStep: (SettingsItem, Int) -> Unit,
    onOpenList: (SettingsItem) -> Unit,
    onReduced: (Boolean) -> Unit,
) {
    var firstTaken = false
    // Every row can be given focus by name (a list closing returns to its row); the first one is also where OK or
    // Right on the section lands.
    fun at(item: SettingsItem): Modifier {
        val own = Modifier.focusRequester(host.rowFocus(item))
        return if (firstTaken) own else { firstTaken = true; own.focusRequester(host.first) }
    }
    groupsOf(SettingsSection.Appearance).forEachIndexed { g, group ->
        // A new group starts 72 under the last row: the rows' own 12, this 12, the 12 after it, and the label's 36.
        if (g > 0) Spacer(Modifier.height(px(12)))
        GroupLabel(group.label)
        group.items.forEach { item ->
            when (item) {
                SettingsItem.Theme -> SettingRow(
                    titleOf(item), { onOpenList(item) }, at(item),
                    ending = RowEnding.Value(now.look.theme.label, chevron = true),
                    held = openList == item,
                    onFocused = { host.onFocused(item) },
                )
                SettingsItem.Accent -> SettingRow(
                    titleOf(item), {}, at(item),
                    description = now.look.accent.label,
                    ending = RowEnding.Dots(AccentId.entries.map { it.argb }, now.look.accent.ordinal),
                    onPill = { onStep(item, it) },
                    onFocused = { host.onFocused(item) },
                )
                // Left and Right step these two in place, as the board's hints say.
                SettingsItem.TextSize, SettingsItem.Clock -> SettingRow(
                    titleOf(item), {}, at(item),
                    ending = RowEnding.Pills(choiceOptions(item), chosenIndex(item, now, tvIs24)),
                    onPill = { onStep(item, it) },
                    onFocused = { host.onFocused(item) },
                )
                SettingsItem.FocusHighlight -> {
                    val row = focusHighlightRow(now.look.focus, reduced)
                    // With Reduced effects on there is nothing to choose: the row states what shows and takes no focus.
                    if (row.canFocus) SettingRow(
                        titleOf(item), { onOpenList(item) }, at(item),
                        ending = RowEnding.Value(row.value, chevron = true),
                        held = openList == item,
                        onFocused = { host.onFocused(item) },
                    ) else SettingRow(titleOf(item), null, description = row.description, ending = RowEnding.Value(row.value, chevron = false))
                }
                SettingsItem.ReducedEffects -> SettingRow(
                    titleOf(item), { onReduced(!reduced) }, at(item),
                    description = "No glow or animation, for a steadier picture",
                    ending = RowEnding.Switch(reduced),
                    onFocused = { host.onFocused(item) },
                )
                else -> Unit
            }
        }
    }
}
