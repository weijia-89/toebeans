package app.toebeans.core.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ADR-0011 banner rule. The most recent ten dose rows count. A row shows the
 * banner when it fired and has no delivery record.
 */
class NotificationDenialBannerTest {
    @Test
    fun `banner copy is the ADR sentence`() {
        assertEquals(
            "Your last dose alarm did not show as a notification because notifications are disabled for toebeans. Tap to fix in Settings, or open the History tab to see what fired.",
            NOTIFICATION_DENIAL_BANNER_COPY,
        )
    }

    @Test
    fun `a fired dose with no delivery shows the banner`() {
        assertTrue(
            notificationDenialBannerVisible(
                listOf(RecentDoseDelivery(fired = true, delivered = false)),
            ),
        )
    }

    @Test
    fun `a delivered dose does not show the banner`() {
        assertFalse(
            notificationDenialBannerVisible(
                listOf(RecentDoseDelivery(fired = true, delivered = true)),
            ),
        )
    }

    @Test
    fun `an unfired dose does not show the banner`() {
        assertFalse(
            notificationDenialBannerVisible(
                listOf(RecentDoseDelivery(fired = false, delivered = false)),
            ),
        )
    }

    @Test
    fun `only the most recent ten rows count`() {
        val olderUndelivered = RecentDoseDelivery(fired = true, delivered = false)
        val recentDelivered = List(10) { RecentDoseDelivery(fired = true, delivered = true) }
        assertFalse(
            notificationDenialBannerVisible(recentDelivered + olderUndelivered),
        )
    }
}
