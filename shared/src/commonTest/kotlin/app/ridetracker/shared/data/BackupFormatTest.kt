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
    }
}
