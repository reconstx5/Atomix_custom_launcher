package dev.atomix.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import coil.ImageLoader
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.apps.InputEntry
import dev.atomix.launcher.data.Item
import dev.atomix.launcher.ui.theme.px

/** Everything the home screen can ask the activity to do. */
class HomeActions(
    val openWeb: (route: String) -> Unit,
    val openApp: (AppEntry) -> Unit,
    val openApps: () -> Unit,
    val openLiveTv: () -> Unit,
    val listInputs: () -> List<InputEntry>,
    val openInput: (InputEntry) -> Unit,
    val switchProfile: () -> Unit,
    val moveDockApp: (pkg: String, delta: Int) -> Unit = { _, _ -> },
    val removeDockApp: (pkg: String) -> Unit = {},
)

@Composable
fun HomeScreen(vm: HomeViewModel, loader: ImageLoader, imageUrl: (String?) -> String?, actions: HomeActions) {
    val state by vm.state.collectAsState()
    val focusedState = vm.focusedItem.collectAsState()
    val offline = state.status == HomeState.Status.Offline
    // The hero follows the focused card; before any card has had focus it shows Atomix's own hero.
    // Read through derived state so a focus move recomposes only the hero layers, not the whole screen.
    val shownState = remember {
        derivedStateOf {
            val s = vm.state.value
            if (s.status == HomeState.Status.Offline) null
            else focusedState.value?.takeIf { f -> s.row?.items?.any { it.id == f.id } == true } ?: s.hero
        }
    }
    val hasShown by remember { derivedStateOf { shownState.value != null } }

    val playFocus = remember { FocusRequester() }
    val dockFocus = remember { FocusRequester() }
    var inputs by remember { mutableStateOf<List<InputEntry>?>(null) }
    var editing by remember { mutableStateOf<AppEntry?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }

    BackHandler { /* Home is the bottom of the stack: Back does nothing here. */ }

    LaunchedEffect(state.status, hasShown) {
        if (initialFocusDone || state.status == HomeState.Status.Loading && !hasShown) return@LaunchedEffect
        initialFocusDone = runCatching { if (hasShown) playFocus.requestFocus() else dockFocus.requestFocus() }.isSuccess
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackdropLayer({ shownState.value }, offline, imageUrl, loader)
        TopBar(state.profileName, onProfile = actions.switchProfile)
        Column(Modifier.padding(top = px(110))) {
            HeroInfoLayer(
                item = { shownState.value },
                offline = offline,
                imageUrl = imageUrl,
                loader = loader,
                playFocus = playFocus,
                onPlay = { actions.openWeb("#/play/${it.id}") },
                onInfo = { actions.openWeb("#/item/${it.id}") },
                modifier = Modifier.padding(start = px(200)),
            )
            val row = state.row
            if (row != null && !offline) {
                ContinueRow(
                    row = row,
                    imageUrl = imageUrl,
                    loader = loader,
                    onFocused = vm::onCardFocused,
                    onOpen = { actions.openWeb("#/play/${it.id}") },
                    modifier = Modifier.padding(top = px(26)),
                )
            }
        }
        Dock(
            apps = state.dock,
            loader = loader,
            firstFocus = dockFocus,
            onApp = actions.openApp,
            onAppLongPress = { editing = it },
            onLiveTv = actions.openLiveTv,
            onInputs = { inputs = actions.listInputs() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = px(36)),
        )
        // Drawn after the hero text so the expanded rail sits on top of it.
        Rail(
            listOf(
                RailEntry(LineIcon.Home, "Home") { runCatching { (if (shownState.value != null) playFocus else dockFocus).requestFocus() } },
                RailEntry(LineIcon.Apps, "Apps", onClick = actions.openApps),
                RailEntry(LineIcon.Library, "Library", enabled = !offline) {
                    actions.openWeb(state.libraryId?.let { "#/library/$it" } ?: "#/")
                },
                RailEntry(LineIcon.Live, "Live", onClick = actions.openLiveTv),
                RailEntry(LineIcon.Inputs, "Inputs") { inputs = actions.listInputs() },
                RailEntry(LineIcon.Search, "Search", enabled = !offline) { actions.openWeb("#/search") },
            ),
            Modifier.padding(start = px(48), top = px(200)),
        )
        editing?.let { app ->
            GlassMenu(
                app.label,
                listOf(
                    MenuOption("Move left") { actions.moveDockApp(app.packageName, -1); vm.refreshDock() },
                    MenuOption("Move right") { actions.moveDockApp(app.packageName, +1); vm.refreshDock() },
                    MenuOption("Remove from dock") { actions.removeDockApp(app.packageName); vm.refreshDock() },
                ),
                onDismiss = { editing = null },
            )
        }
        inputs?.let { list ->
            InputsDialog(list, onPick = { inputs = null; actions.openInput(it) }, onDismiss = { inputs = null })
        }
    }
}

/** Reads the shown item here, so only this layer recomposes when focus moves. */
@Composable
private fun HeroBackdropLayer(item: () -> Item?, offline: Boolean, imageUrl: (String?) -> String?, loader: ImageLoader) =
    HeroBackdrop(item(), offline, imageUrl, loader)

@Composable
private fun HeroInfoLayer(
    item: () -> Item?,
    offline: Boolean,
    imageUrl: (String?) -> String?,
    loader: ImageLoader,
    playFocus: FocusRequester,
    onPlay: (Item) -> Unit,
    onInfo: (Item) -> Unit,
    modifier: Modifier = Modifier,
) = HeroInfo(item(), offline, imageUrl, loader, playFocus, onPlay, onInfo, modifier)
