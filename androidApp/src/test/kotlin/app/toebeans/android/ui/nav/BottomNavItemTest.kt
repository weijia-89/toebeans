package app.toebeans.android.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomNavItemTest {
    @Test
    fun `bottom nav entry order is today pets reminders settings`() {
        assertEquals(
            listOf(
                BottomNavItem.HOME,
                BottomNavItem.PETS,
                BottomNavItem.REMINDERS,
                BottomNavItem.SETTINGS,
            ),
            BottomNavItem.entries.toList(),
        )
    }
}
