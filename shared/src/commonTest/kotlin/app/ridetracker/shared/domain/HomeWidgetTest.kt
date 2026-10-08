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
}
