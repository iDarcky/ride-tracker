package app.ridetracker.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.ridetracker.shared.domain.Country
import app.ridetracker.shared.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import okio.Path.Companion.toPath

data class AppSettings(
    /** Null until the user picks a country on the welcome screen. */
    val country: Country?,
    /** ISO 4217 code chosen for [Country.OTHER]; ignored for countries with a fixed currency. */
    val otherCurrencyCode: String?,
    val firstDayOfWeek: DayOfWeek,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    /** The app currency, or null when the platform default should be used. */
    val currencyCode: String? get() = country?.currencyCode ?: otherCurrencyCode
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            country = Country.fromId(prefs[COUNTRY]),
            otherCurrencyCode = prefs[CURRENCY],
            firstDayOfWeek = DayOfWeek(prefs[FIRST_DAY_OF_WEEK] ?: DayOfWeek.MONDAY.isoDayNumber),
            themeMode = ThemeMode.fromId(prefs[THEME]),
        )
    }

    /** Saves the country; [otherCurrencyCode] is only kept for [Country.OTHER]. */
    suspend fun setCountry(country: Country, otherCurrencyCode: String? = null) {
        dataStore.edit {
            it[COUNTRY] = country.id
            if (otherCurrencyCode != null) it[CURRENCY] = otherCurrencyCode
        }
    }

    suspend fun setFirstDayOfWeek(day: DayOfWeek) {
        dataStore.edit { it[FIRST_DAY_OF_WEEK] = day.isoDayNumber }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.id }
    }

    companion object {
        const val FILE_NAME = "settings.preferences_pb"
        private val COUNTRY = stringPreferencesKey("country")
        private val CURRENCY = stringPreferencesKey("currency_code")
        private val FIRST_DAY_OF_WEEK = intPreferencesKey("first_day_of_week")
        private val THEME = stringPreferencesKey("theme_mode")

        fun create(absolutePath: String): SettingsRepository =
            SettingsRepository(PreferenceDataStoreFactory.createWithPath(produceFile = { absolutePath.toPath() }))
    }
}
