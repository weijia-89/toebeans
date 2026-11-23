package app.toebeans.android.ui.nav

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.toebeans.core.navigation.ToebeansRootComponent
import app.toebeans.core.navigation.ToebeansTab

/**
 * Top-level tabs whose screen owns a scrollable list. Settings has no list, so it never
 * receives a [LazyListState] from the shell and is excluded from scroll restoration.
 */
internal val SCROLLING_TABS: Set<ToebeansTab> =
    setOf(ToebeansTab.TODAY, ToebeansTab.MED_LIST, ToebeansTab.PETS, ToebeansTab.HISTORY)

/**
 * Saves the outgoing tab's first-visible position into the shared model.
 *
 * The model keeps the pixel offset in its existing single-Int `scrollOffset` contract.
 * The first-visible item index is kept in [indices] so both halves can be replayed with
 * one [LazyListState.scrollToItem] call when the tab is entered again.
 */
internal fun saveTabScrollPosition(
    rootComponent: ToebeansRootComponent,
    indices: MutableMap<ToebeansTab, Int>,
    tab: ToebeansTab,
    listState: LazyListState,
) {
    indices[tab] = listState.firstVisibleItemIndex
    rootComponent.saveScrollOffset(listState.firstVisibleItemScrollOffset)
}

/**
 * Applies a restored position to a real [LazyListState] on tab entry (ADR-0023 gap 1).
 *
 * [restoreKey] is the tab being entered, so switching tabs replays that tab's saved
 * position. A fresh tab has both halves at zero, which makes the call a no-op.
 */
@Composable
internal fun ApplyRestoredScroll(
    listState: LazyListState,
    restoredIndex: Int,
    restoredOffset: Int,
    restoreKey: Any?,
) {
    LaunchedEffect(restoreKey) {
        if (restoredIndex > 0 || restoredOffset > 0) {
            listState.scrollToItem(index = restoredIndex, scrollOffset = restoredOffset)
        }
    }
}
