package app.ridetracker.shared.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Guards the backup file format: old backups must keep restoring in new versions. */
class BackupFormatTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun readsVersion1Backup() {
        val text = """
            {
              "format": "ridetracker-backup", "formatVersion": 1, "appVersion": "0.0.4",
              "createdAtEpochMillis": 1791331200000,
              "settings": { "country": "RO", "firstDayOfWeek": 1, "themeMode": "dark", "drivingType": "pfa" },
              "platforms": [ { "id": 1, "name": "Uber", "colorArgb": 4278190080, "sortOrder": 0, "archived": false } ],
              "incomeEntries": [
                { "id": 7, "platformId": 1, "amountMinor": 12050, "date": "2026-10-07", "note": null, "createdAtEpochMillis": 1 }
              ],
              "someFutureField": true
            }
        """.trimIndent()
        val file = json.decodeFromString(BackupFile.serializer(), text)
        assertEquals("RO", file.settings.country)
        assertEquals(12050, file.entries.single().amountMinor)
        assertEquals("2026-10-07", file.entries.single().date)
        assertEquals(emptyList(), file.expenses) // format 1 has no expenses
    }

    @Test
    fun readsVersion2BackupWithExpenses() {
        val text = """
            {
              "format": "ridetracker-backup", "formatVersion": 2, "appVersion": "0.0.6",
              "createdAtEpochMillis": 1, "settings": {}, "platforms": [], "incomeEntries": [],
              "expenses": [ { "id": 3, "amountMinor": 25000, "date": "2026-10-08", "category": "fuel", "createdAtEpochMillis": 2 } ]
            }
        """.trimIndent()
        val expense = json.decodeFromString(BackupFile.serializer(), text).expenses.single()
        assertEquals("fuel", expense.category)
        assertEquals(25000, expense.amountMinor)
    }

    @Test
    fun readsVersion3BackupWithVehicle() {
        val text = """
            {
              "format": "ridetracker-backup", "formatVersion": 3, "appVersion": "0.0.7",
              "createdAtEpochMillis": 1, "settings": {}, "platforms": [], "incomeEntries": [],
              "vehicles": [ { "id": 1, "name": "Test car", "year": 2012, "fuelType": "diesel", "consumptionCenti": 700, "fuelPriceMinor": 750 } ],
              "odometerReadings": [ { "id": 1, "vehicleId": 1, "date": "2026-09-28", "km": 235000, "createdAtEpochMillis": 0 } ]
            }
        """.trimIndent()
        val file = json.decodeFromString(BackupFile.serializer(), text)
        assertEquals(700, file.vehicles.single().consumptionCenti)
        assertEquals(235000, file.odometerReadings.single().km)
    }
}
