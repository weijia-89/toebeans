package app.toebeans.android.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.toebeans.core.navigation.DefaultToebeansRootComponent
import app.toebeans.core.navigation.ToebeansTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-0023 gap 1: a scroll offset restored from the shared model must reach the real
 * Compose list state, not just stay modelled in the component. This renders a real
 * [LazyColumn] and drives it through the same [ApplyRestoredScroll] helper the shell uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TabScrollRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun restoredModelOffsetReachesTheRealListState() {
        val component = DefaultToebeansRootComponent()
        component.selectTab(ToebeansTab.MED_LIST)
        component.saveScrollOffset(240)
        component.selectTab(ToebeansTab.TODAY)
        component.selectTab(ToebeansTab.MED_LIST)
        val restoredOffset = component.childStack.value.active.instance.scrollOffset
        assertEquals(240, restoredOffset)

        val listState = LazyListState()
        rule.setContent {
            ApplyRestoredScroll(
                listState = listState,
                restoredIndex = 0,
                restoredOffset = restoredOffset,
                restoreKey = ToebeansTab.MED_LIST,
            )
            // Tall rows keep the 240px offset inside item 0, so the assertion is exact.
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(50) { index ->
                    Text(
                        text = "row $index",
                        modifier = Modifier.fillMaxWidth().height(400.dp),
                    )
                }
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            listState.firstVisibleItemScrollOffset == 240
        }

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(240, listState.firstVisibleItemScrollOffset)
    }
}
