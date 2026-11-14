@file:OptIn(ExperimentalComposeUiApi::class) // FocusRequester.Cancel

package dev.atomix.launcher.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import coil.ImageLoader
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.shell.FocusMemory
import dev.atomix.launcher.shell.FocusTarget
import dev.atomix.launcher.shell.LocalTopBarFocus
import dev.atomix.launcher.ui.kit.AppTile
import dev.atomix.launcher.ui.kit.AppTileSize
import dev.atomix.launcher.ui.kit.HintSpot
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.ScreenFrame
import dev.atomix.launcher.ui.kit.TextAction
import dev.atomix.launcher.ui.kit.WindowColumn
import dev.atomix.launcher.ui.kit.WindowState
import dev.atomix.launcher.ui.kit.hintsFor
import dev.atomix.launcher.ui.theme.px

/** The Apps tab's only row; its focus memory is saved as "appsTab". */
const val ROW_ALL_APPS = "all"

/** Seven tiles across, and four rows at a time: twenty-eight apps on one screen. */
internal const val APP_COLUMNS = 7
private const val ROWS_SHOWN = 4

/**
 * Every launchable app as a tile, seven across; a small house marks the ones in Home's "Your apps". OK opens the app;
 * "Edit" opens manage apps. Focus comes back to the remembered tile on every [restoreTick].
 */
@Composable
fun AppsTab(
    state: AppsTabState,
    loader: ImageLoader,
    memory: FocusMemory,
    restoreTick: Int,
    onLaunch: (AppEntry) -> Unit,
    onEdit: () -> Unit,
) {
    val apps by state.apps.collectAsState()
    val rows = remember(apps) { apps.chunked(APP_COLUMNS) }
    val window = remember { WindowState() }
    val tileFocus = remember { HashMap<String, FocusRequester>() }
    val editFocus = remember { FocusRequester() }
    val bar = LocalTopBarFocus.current
    var focusedPkg by remember { mutableStateOf(memory.current()?.key) }
    var onEditAction by remember { mutableStateOf(false) }

    LaunchedEffect(restoreTick, apps.size) {
        val target = memory.resolve(mapOf(ROW_ALL_APPS to apps.map { it.packageName })) ?: return@LaunchedEffect
        window.show(target.index / APP_COLUMNS, rows.size, ROWS_SHOWN)
        withFrameNanos { }
        runCatching { tileFocus[target.key]?.requestFocus() }
    }

    /** The tile's requester if its row is one of those showing. */
    fun showing(pkg: String?): FocusRequester? {
        val index = apps.indexOfFirst { it.packageName == pkg }
        if (index < 0) return null
        val first = window.startFor(rows.size, ROWS_SHOWN)
        return if (index / APP_COLUMNS in first until first + ROWS_SHOWN) tileFocus[pkg] else null
    }

    // Down from the top bar returns to the tile focus was on.
    val barDown by rememberUpdatedState { showing(focusedPkg) ?: FocusRequester.Default }
    DisposableEffect(bar) {
        bar?.down = { barDown() }
        onDispose { bar?.down = null }
    }

    ScreenFrame(
        heading = "Apps",
        // Read where the hint bar is drawn, so moving focus across the grid does not redraw all of its tiles.
        hints = {
            val focusedApp = apps.firstOrNull { it.packageName == focusedPkg }
            when {
                onEditAction -> hintsFor(HintSpot.AppsEdit)
                focusedApp != null -> hintsFor(HintSpot.AppTile(focusedApp.label))
                else -> emptyList()
            }
        },
        action = {
            TextAction(
                "Edit", LineIcon.Edit, onEdit,
                Modifier.focusRequester(editFocus).onFocusChanged { onEditAction = it.isFocused }.focusProperties {
                    // Back down to the tile under it: the last one of the top row.
                    showing(rows.firstOrNull()?.lastOrNull()?.packageName)?.let { down = it }
                },
            )
        },
    ) {
        WindowColumn(rows, { it.first().packageName }, window, ROWS_SHOWN, rowPx = AppTileSize.Grid.height, gapPx = 18, modifier = Modifier.fillMaxWidth()) { rowIndex, row, slot ->
            Row(slot.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(18))) {
                row.forEachIndexed { column, app ->
                    key(app.packageName) {
                        AppTile(
                            app, loader, AppTileSize.Grid,
                            onClick = { onLaunch(app) },
                            modifier = Modifier.weight(1f)
                                .focusRequester(tileFocus.getOrPut(app.packageName) { FocusRequester() })
                                .focusProperties {
                                    // Up from the top row: the tile under "Edit" goes to it, the rest to the Apps tab.
                                    if (rowIndex == 0) {
                                        if (column == APP_COLUMNS - 1) up = editFocus else if (bar != null) up = bar.marked
                                    }
                                    // A row ends at its last tile: Right must not wander off to "Edit" or to a
                                    // longer row above.
                                    if (column == 0) left = FocusRequester.Cancel
                                    if (column == row.lastIndex) right = FocusRequester.Cancel
                                },
                            onHome = state.isFavourite(app.packageName),
                            onFocused = {
                                focusedPkg = app.packageName
                                memory.remember(FocusTarget(ROW_ALL_APPS, app.packageName, rowIndex * APP_COLUMNS + column))
                            },
                        )
                    }
                }
                // A short last row keeps its tiles the same width as the rows above.
                repeat(APP_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
