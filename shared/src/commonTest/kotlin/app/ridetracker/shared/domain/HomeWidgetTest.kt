package app.ridetracker.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeWidgetTest {
    @Test
    fun defaultWhenNothingStored() {
        assertEquals(HomeWidget.DEFAULT, HomeWidget.parse(null))
        assertEquals(false, HomeWidget.WHEN_YOU_EARN in HomeWidget.DEFAULT)
    }

    @Test
    fun keepsOrderAndSkipsUnknownIds() {
        val stored = "activity, split,widget_from_the_future,activity"
        assertEquals(listOf(HomeWidget.ACTIVITY, HomeWidget.SPLIT), HomeWidget.parse(stored))
    }

    @Test
    fun emptyMeansEverythingHidden() {
        assertEquals(emptyList(), HomeWidget.parse(""))
    }

    @Test
    fun roundTrips() {
        val layout = listOf(HomeWidget.WHEN_YOU_EARN, HomeWidget.BREAKDOWN)
        assertEquals(layout, HomeWidget.parse(HomeWidget.format(layout)))
    }

    @Test
    fun newCardsShowUpOnceInACustomisedLayout() {
        val custom = listOf(HomeWidget.TARGET, HomeWidget.SPLIT, HomeWidget.BREAKDOWN)
        // Saved before cards were tracked: the comparison card is new and goes after Breakdown, its place in the default.
        assertEquals(
            listOf(
                HomeWidget.TARGET, HomeWidget.WEEK_SCREENSHOTS, HomeWidget.WEEK_PAYOUT, HomeWidget.SPLIT, HomeWidget.BREAKDOWN,
                HomeWidget.COMPARISON, HomeWidget.REAL_PER_HOUR,
            ),
            HomeWidget.withNew(custom, seen = null),
        )
        // Once the driver saves a layout with every card seen, a removed card stays removed.
        assertEquals(custom, HomeWidget.withNew(custom, seen = HomeWidget.entries.map { it.id }.toSet()))
    }
}
