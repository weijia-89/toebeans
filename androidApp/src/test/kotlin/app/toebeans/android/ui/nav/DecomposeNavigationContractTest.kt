package app.toebeans.android.ui.nav

import app.toebeans.core.navigation.DefaultToebeansRootComponent
import app.toebeans.core.navigation.DoseDestination
import app.toebeans.core.navigation.ToebeansConfig
import app.toebeans.core.navigation.ToebeansTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Decompose navigation migration (ADR-0023). These cases run against the
 * shared commonMain component, so the same model can be verified on Android and iOS.
 */
class DecomposeNavigationContractTest {
    @Test
    fun allFiveTabsAreReachable() {
        val component = DefaultToebeansRootComponent()
        assertEquals(ToebeansTab.entries.toList(), component.tabs)
        ToebeansTab.entries.forEach { tab ->
            component.selectTab(tab)
            assertEquals(tab, component.selectedTab.value)
            assertEquals(ToebeansConfig.Tab(tab), component.childStack.value.active.configuration)
        }
    }

    @Test
    fun doseLoggingRouteReachableFromToday() {
        val component = DefaultToebeansRootComponent()
        component.openDoseLogging(petId = "pet-1", medicationId = "med-2", scheduleId = null)
        assertEquals(DoseDestination.SCHEDULE_CREATE, component.activeDoseDestination())
        assertEquals(
            ToebeansConfig.DoseLogging("pet-1", "med-2", null, DoseDestination.SCHEDULE_CREATE),
            component.childStack.value.active.configuration,
        )

        component.back()
        component.openDoseLogging(petId = "pet-1", medicationId = "med-2", scheduleId = "sched-3")
        assertEquals(DoseDestination.MEDICATION_EDIT, component.activeDoseDestination())
    }

    @Test
    fun backStackAndTabStateAreRestored() {
        val component = DefaultToebeansRootComponent()
        component.openDoseLogging(petId = "pet-1", medicationId = "med-2", scheduleId = "sched-3")
        assertEquals(2, component.childStack.value.items.size)
        assertTrue(component.back())
        assertEquals(ToebeansConfig.Tab(ToebeansTab.TODAY), component.childStack.value.active.configuration)
        assertFalse(component.back())

        component.selectTab(ToebeansTab.PETS)
        component.saveScrollOffset(120)
        component.selectTab(ToebeansTab.SETTINGS)
        component.selectTab(ToebeansTab.PETS)
        assertEquals(120, component.childStack.value.active.instance.scrollOffset)
    }

    /**
     * ADR-0023 gap 2: a back event (hardware back, explicit onBack, or onSaved) must
     * update the shared model, not only the NavHost. [handleBackEvent] is the single path
     * the shell uses for all three, so this case proves the model stays in sync.
     */
    @Test
    fun backEventKeepsSharedModelInSync() {
        val component = DefaultToebeansRootComponent()
        component.openDoseLogging(petId = "pet-1", medicationId = "med-2", scheduleId = "sched-3")
        assertEquals(DoseDestination.MEDICATION_EDIT, component.activeDoseDestination())

        // A back event that pops the NavHost also pops the shared dose config.
        val popped = handleBackEvent(component) { true }
        assertTrue(popped)
        assertNull(component.activeDoseDestination())
        assertEquals(1, component.childStack.value.items.size)
        assertEquals(ToebeansConfig.Tab(ToebeansTab.TODAY), component.childStack.value.active.configuration)

        // A back event with no destination to pop leaves the shared model untouched.
        val consumed = handleBackEvent(component) { false }
        assertFalse(consumed)
        assertEquals(ToebeansConfig.Tab(ToebeansTab.TODAY), component.childStack.value.active.configuration)
    }
}
