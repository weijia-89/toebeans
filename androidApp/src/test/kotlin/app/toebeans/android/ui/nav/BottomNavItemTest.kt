package app.toebeans.android.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomNavItemTest {
    @Test
    fun `bottom nav entry order is today reminders pets settings`() {
        assertEquals(
            listOf(
                BottomNavItem.HOME,
                BottomNavItem.REMINDERS,
                BottomNavItem.PETS,
                BottomNavItem.SETTINGS,
            ),
            BottomNavItem.entries.toList(),
        )
    }
}
