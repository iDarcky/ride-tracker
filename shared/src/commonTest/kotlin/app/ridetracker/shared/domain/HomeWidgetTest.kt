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
    fun aLayoutFromBeforeTheShortHomeSwitchesToItOnce() {
        val custom = listOf(HomeWidget.SPLIT, HomeWidget.BREAKDOWN)
        assertEquals(HomeWidget.DEFAULT, HomeWidget.withNew(custom, seen = null))
        val before = HomeWidget.entries.map { it.id }.toSet() - HomeWidget.MONTH_DETAIL.id
        assertEquals(HomeWidget.DEFAULT, HomeWidget.withNew(custom, seen = before))
        // Once the driver saves a layout with every card seen, it stays theirs.
        assertEquals(custom, HomeWidget.withNew(custom, seen = HomeWidget.SEEN.toSet()))
    }

    @Test
    fun theRingShowsUpOnceUnderMoneyKept() {
        val saved = listOf(HomeWidget.WEEK_SCREENSHOTS, HomeWidget.WEEK_PAYOUT, HomeWidget.MONTH_DETAIL)
        val before = HomeWidget.entries.map { it.id }.toSet()
        assertEquals(
            listOf(HomeWidget.WEEK_SCREENSHOTS, HomeWidget.BREAKDOWN, HomeWidget.WEEK_PAYOUT, HomeWidget.MONTH_DETAIL),
            HomeWidget.withNew(saved, seen = before),
        )
        // Already there further down: it moves under money kept.
        assertEquals(
            listOf(HomeWidget.WEEK_SCREENSHOTS, HomeWidget.BREAKDOWN, HomeWidget.WEEK_PAYOUT, HomeWidget.MONTH_DETAIL),
            HomeWidget.withNew(saved + HomeWidget.BREAKDOWN, seen = before),
        )
        // Removed after that, it stays removed.
        assertEquals(saved, HomeWidget.withNew(saved, seen = HomeWidget.SEEN.toSet()))
    }

    @Test
    fun targetIsNoLongerACard() {
        assertEquals(listOf(HomeWidget.SPLIT), HomeWidget.parse("target,split"))
        assertEquals(false, HomeWidget.TARGET in HomeWidget.MOVABLE)
    }
}
