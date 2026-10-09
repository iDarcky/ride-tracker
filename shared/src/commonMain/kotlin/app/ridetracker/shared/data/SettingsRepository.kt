package app.ridetracker.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.ridetracker.shared.domain.Country
import app.ridetracker.shared.domain.DrivingType
import app.ridetracker.shared.domain.HomeWidget
import app.ridetracker.shared.domain.ThemeMode
import app.ridetracker.shared.domain.ZReportReminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    /** Only asked in Romania. */
    val drivingType: DrivingType? = null,
    /** Cards shown on Home, in order (see [HomeWidget]). */
    val homeWidgets: List<HomeWidget> = HomeWidget.DEFAULT,
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
            drivingType = DrivingType.fromId(prefs[DRIVING_TYPE]),
            homeWidgets = HomeWidget.parse(prefs[HOME_WIDGETS]),
        )
    }

    /** The Raportul Z reminder (Romania). Kept apart from [AppSettings]: it isn't in backups and survives a restore. */
    val zReport: Flow<ZReportReminder> = dataStore.data.map { prefs ->
        ZReportReminder(
            enabled = prefs[Z_ENABLED] ?: false,
            minuteOfDay = prefs[Z_MINUTE] ?: ZReportReminder.DEFAULT_MINUTE,
            doneThrough = prefs[Z_DONE_THROUGH],
            enabledFrom = prefs[Z_ENABLED_FROM],
            suggestionDismissed = prefs[Z_SUGGESTION_DISMISSED] ?: false,
        )
    }

    /** Turns the reminder on or off; turning it on starts from [todayEpochDay] (earlier days never wait). */
    suspend fun setZReportEnabled(enabled: Boolean, todayEpochDay: Long) {
        dataStore.edit {
            if (enabled && it[Z_ENABLED] != true) it[Z_ENABLED_FROM] = todayEpochDay
            it[Z_ENABLED] = enabled
        }
    }

    suspend fun setZReportTime(minuteOfDay: Int) {
        dataStore.edit { it[Z_MINUTE] = minuteOfDay.coerceIn(0, 24 * 60 - 1) }
    }

    /** "Gata": the Z report for [epochDay] (and every day before it) is done. */
    suspend fun markZReportDone(epochDay: Long) {
        dataStore.edit { prefs -> prefs[Z_DONE_THROUGH] = maxOf(epochDay, prefs[Z_DONE_THROUGH] ?: Long.MIN_VALUE) }
    }

    suspend fun dismissZReportSuggestion() {
        dataStore.edit { it[Z_SUGGESTION_DISMISSED] = true }
    }

    suspend fun setHomeWidgets(widgets: List<HomeWidget>) {
        dataStore.edit { it[HOME_WIDGETS] = HomeWidget.format(widgets) }
    }

    /** Saves the country; [otherCurrencyCode] is only kept for [Country.OTHER]. */
    suspend fun setCountry(country: Country, otherCurrencyCode: String? = null, drivingType: DrivingType? = null) {
        dataStore.edit {
            it[COUNTRY] = country.id
            if (otherCurrencyCode != null) it[CURRENCY] = otherCurrencyCode
            if (drivingType != null) it[DRIVING_TYPE] = drivingType.id
        }
    }

    suspend fun setDrivingType(type: DrivingType) {
        dataStore.edit { it[DRIVING_TYPE] = type.id }
    }

    suspend fun setFirstDayOfWeek(day: DayOfWeek) {
        dataStore.edit { it[FIRST_DAY_OF_WEEK] = day.isoDayNumber }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.id }
    }

    suspend fun current(): AppSettings = settings.first()

    /** Replaces every setting (used by restore). */
    suspend fun replaceAll(settings: AppSettings) {
        dataStore.edit { prefs ->
            val zKeys = listOf(Z_ENABLED, Z_SUGGESTION_DISMISSED).associateWith { prefs[it] }
            val zLongs = listOf(Z_DONE_THROUGH, Z_ENABLED_FROM).associateWith { prefs[it] }
            val zMinute = prefs[Z_MINUTE]
            prefs.clear()
            zKeys.forEach { (key, value) -> if (value != null) prefs[key] = value }
            zLongs.forEach { (key, value) -> if (value != null) prefs[key] = value }
            if (zMinute != null) prefs[Z_MINUTE] = zMinute
            settings.country?.let { prefs[COUNTRY] = it.id }
            settings.otherCurrencyCode?.let { prefs[CURRENCY] = it }
            prefs[FIRST_DAY_OF_WEEK] = settings.firstDayOfWeek.isoDayNumber
            prefs[THEME] = settings.themeMode.id
            settings.drivingType?.let { prefs[DRIVING_TYPE] = it.id }
            prefs[HOME_WIDGETS] = HomeWidget.format(settings.homeWidgets)
        }
    }

    /** Back to first-launch state: the welcome screen shows again. */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        const val FILE_NAME = "settings.preferences_pb"
        private val COUNTRY = stringPreferencesKey("country")
        private val CURRENCY = stringPreferencesKey("currency_code")
        private val FIRST_DAY_OF_WEEK = intPreferencesKey("first_day_of_week")
        private val THEME = stringPreferencesKey("theme_mode")
        private val DRIVING_TYPE = stringPreferencesKey("driving_type")
        private val HOME_WIDGETS = stringPreferencesKey("home_widgets")
        private val Z_ENABLED = booleanPreferencesKey("z_report_enabled")
        private val Z_MINUTE = intPreferencesKey("z_report_minute")
        private val Z_DONE_THROUGH = longPreferencesKey("z_report_done_through")
        private val Z_ENABLED_FROM = longPreferencesKey("z_report_enabled_from")
        private val Z_SUGGESTION_DISMISSED = booleanPreferencesKey("z_report_suggestion_dismissed")

        fun create(absolutePath: String): SettingsRepository =
            SettingsRepository(PreferenceDataStoreFactory.createWithPath(produceFile = { absolutePath.toPath() }))
    }
}
