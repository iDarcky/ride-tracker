package app.ridetracker.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpenseCategoryTest {
    @Test
    fun idsAreUnique() {
        assertEquals(ExpenseCategory.entries.size, ExpenseCategory.entries.map { it.id }.toSet().size)
    }

    @Test
    fun unknownIdFallsBackToOther() {
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.fromId("something-from-a-newer-version"))
    }

    @Test
    fun everyGroupHasCategories() {
        ExpenseGroup.entries.forEach { check(ExpenseCategory.inGroup(it).isNotEmpty()) }
    }
}
