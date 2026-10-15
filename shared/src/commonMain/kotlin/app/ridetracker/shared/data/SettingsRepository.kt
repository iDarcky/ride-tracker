package app.ridetracker.shared.data

import app.ridetracker.shared.domain.DistanceUnit
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
import app.ridetracker.shared.domain.ImportReminderSettings
import app.ridetracker.shared.domain.ZReportReminder
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.TargetBasis
import app.ridetracker.shared.domain.TargetSettings
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
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
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
            homeWidgets = prefs[HOME_WIDGETS]?.let { stored ->
                HomeWidget.withNew(HomeWidget.parse(stored), prefs[HOME_WIDGETS_SEEN]?.split(',')?.toSet())
            } ?: HomeWidget.DEFAULT,
            distanceUnit = DistanceUnit.fromId(prefs[DISTANCE_UNIT]),
        )
    }

    /** The Raportul Z reminder (Romania). Kept apart from [AppSettings]: it isn't in backups and survives a restore. */
    val importReminder: Flow<ImportReminderSettings> = dataStore.data.map { prefs ->
        ImportReminderSettings(
            enabled = prefs[IMPORT_REMINDER_ENABLED] ?: false,
            minuteOfDay = prefs[IMPORT_REMINDER_MINUTE] ?: ImportReminderSettings.DEFAULT_MINUTE,
        )
    }

    suspend fun setImportReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[IMPORT_REMINDER_ENABLED] = enabled }
    }

    suspend fun setImportReminderTime(minuteOfDay: Int) {
        dataStore.edit { it[IMPORT_REMINDER_MINUTE] = minuteOfDay.coerceIn(0, 24 * 60 - 1) }
    }

    /** The weekly backup to a file the driver chose (on Google Drive or the phone). Belongs to this phone, like reminders. */
    val autoBackup: Flow<AutoBackupSettings> = dataStore.data.map { prefs ->
        AutoBackupSettings(
            fileUri = prefs[AUTO_BACKUP_URI],
            lastBackupEpochMillis = prefs[AUTO_BACKUP_LAST],
            lastFailed = prefs[AUTO_BACKUP_FAILED] ?: false,
        )
    }

    /** Turns the weekly backup on with [fileUri], or off with null. */
    suspend fun setAutoBackupFile(fileUri: String?) {
        dataStore.edit {
            if (fileUri == null) it.remove(AUTO_BACKUP_URI) else it[AUTO_BACKUP_URI] = fileUri
            it.remove(AUTO_BACKUP_FAILED)
        }
    }

    suspend fun setAutoBackupResult(success: Boolean, epochMillis: Long) {
        dataStore.edit {
            it[AUTO_BACKUP_FAILED] = !success
            if (success) it[AUTO_BACKUP_LAST] = epochMillis
        }
    }

    val zReport: Flow<ZReportReminder> = dataStore.data.map { prefs ->
        ZReportReminder(
            enabled = prefs[Z_ENABLED] ?: false,
            minuteOfDay = prefs[Z_MINUTE] ?: ZReportReminder.DEFAULT_MINUTE,
            doneThrough = prefs[Z_DONE_THROUGH],
            enabledFrom = prefs[Z_ENABLED_FROM],
            suggestionDismissed = prefs[Z_SUGGESTION_DISMISSED] ?: false,
        )
    }

    /** Monthly targets. Like the Raportul Z reminder, kept out of [AppSettings] and through a restore. */
    val target: Flow<TargetSettings> = dataStore.data.map { prefs ->
        TargetSettings(
            amounts = TargetSettings.parseAmounts(prefs[TARGET_AMOUNTS]),
            basis = TargetBasis.fromId(prefs[TARGET_BASIS]),
            drivingDays = TargetSettings.parseDays(prefs[TARGET_DAYS]),
            notifiedMonth = prefs[TARGET_NOTIFIED],
            suggestionDismissed = prefs[TARGET_SUGGESTION_DISMISSED] ?: false,
        )
    }

    /** The target for [month]; 0 or less removes it. */
    suspend fun setTarget(month: Period.Month, amountMinor: Long) {
        dataStore.edit { prefs ->
            val amounts = TargetSettings.parseAmounts(prefs[TARGET_AMOUNTS]).toMutableMap()
            amounts[TargetSettings.key(month)] = amountMinor.coerceAtLeast(0)
            prefs[TARGET_AMOUNTS] = TargetSettings.formatAmounts(amounts)
            // The month's target can be reached again with a new amount.
            if (prefs[TARGET_NOTIFIED] == TargetSettings.key(month)) prefs.remove(TARGET_NOTIFIED)
            // A Home customised before targets existed doesn't have the card yet: it goes first.
            prefs[HOME_WIDGETS]?.let { stored ->
                val widgets = HomeWidget.parse(stored)
                if (amountMinor > 0 && HomeWidget.TARGET !in widgets) prefs[HOME_WIDGETS] = HomeWidget.format(listOf(HomeWidget.TARGET) + widgets)
            }
        }
    }

    suspend fun setTargetBasis(basis: TargetBasis) {
        dataStore.edit { it[TARGET_BASIS] = basis.id }
    }

    suspend fun setDrivingDays(days: Set<DayOfWeek>) {
        dataStore.edit { it[TARGET_DAYS] = TargetSettings.formatDays(days) }
    }

    suspend fun markTargetNotified(month: Period.Month) {
        dataStore.edit { it[TARGET_NOTIFIED] = TargetSettings.key(month) }
    }

    suspend fun dismissTargetSuggestion() {
        dataStore.edit { it[TARGET_SUGGESTION_DISMISSED] = true }
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

    /** Saves the layout; every card that exists now counts as seen, so one the driver removed stays removed. */
    suspend fun setHomeWidgets(widgets: List<HomeWidget>) {
        dataStore.edit {
            it[HOME_WIDGETS] = HomeWidget.format(widgets)
            it[HOME_WIDGETS_SEEN] = HomeWidget.entries.joinToString(",") { w -> w.id }
        }
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

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        dataStore.edit { it[DISTANCE_UNIT] = unit.id }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.id }
    }

    suspend fun current(): AppSettings = settings.first()

    /** Replaces every setting (used by restore). */
    suspend fun replaceAll(settings: AppSettings) {
        dataStore.edit { prefs ->
            // Reminders and the target belong to this phone, not the backup: they stay as they are.
            val zKeys = listOf(Z_ENABLED, Z_SUGGESTION_DISMISSED, IMPORT_REMINDER_ENABLED).associateWith { prefs[it] }
            val zLongs = listOf(Z_DONE_THROUGH, Z_ENABLED_FROM).associateWith { prefs[it] }
            val zMinute = prefs[Z_MINUTE]
            val importMinute = prefs[IMPORT_REMINDER_MINUTE]
            val backupUri = prefs[AUTO_BACKUP_URI]
            val backupLast = prefs[AUTO_BACKUP_LAST]
            val backupFailed = prefs[AUTO_BACKUP_FAILED]
            val targetStrings = listOf(TARGET_AMOUNTS, TARGET_BASIS, TARGET_DAYS, TARGET_NOTIFIED).associateWith { prefs[it] }
            val targetDismissed = prefs[TARGET_SUGGESTION_DISMISSED]
            prefs.clear()
            targetStrings.forEach { (key, value) -> if (value != null) prefs[key] = value }
            if (targetDismissed != null) prefs[TARGET_SUGGESTION_DISMISSED] = targetDismissed
            zKeys.forEach { (key, value) -> if (value != null) prefs[key] = value }
            zLongs.forEach { (key, value) -> if (value != null) prefs[key] = value }
            if (zMinute != null) prefs[Z_MINUTE] = zMinute
            if (importMinute != null) prefs[IMPORT_REMINDER_MINUTE] = importMinute
            if (backupUri != null) prefs[AUTO_BACKUP_URI] = backupUri
            if (backupLast != null) prefs[AUTO_BACKUP_LAST] = backupLast
            if (backupFailed != null) prefs[AUTO_BACKUP_FAILED] = backupFailed
            settings.country?.let { prefs[COUNTRY] = it.id }
            settings.otherCurrencyCode?.let { prefs[CURRENCY] = it }
            prefs[FIRST_DAY_OF_WEEK] = settings.firstDayOfWeek.isoDayNumber
            prefs[THEME] = settings.themeMode.id
            settings.drivingType?.let { prefs[DRIVING_TYPE] = it.id }
            prefs[HOME_WIDGETS] = HomeWidget.format(settings.homeWidgets)
            prefs[DISTANCE_UNIT] = settings.distanceUnit.id
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
        private val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        private val DRIVING_TYPE = stringPreferencesKey("driving_type")
        private val HOME_WIDGETS = stringPreferencesKey("home_widgets")
        private val HOME_WIDGETS_SEEN = stringPreferencesKey("home_widgets_seen")
        private val IMPORT_REMINDER_ENABLED = booleanPreferencesKey("import_reminder_enabled")
        private val IMPORT_REMINDER_MINUTE = intPreferencesKey("import_reminder_minute")
        private val AUTO_BACKUP_URI = stringPreferencesKey("auto_backup_uri")
        private val AUTO_BACKUP_LAST = longPreferencesKey("auto_backup_last")
        private val AUTO_BACKUP_FAILED = booleanPreferencesKey("auto_backup_failed")
        private val Z_ENABLED = booleanPreferencesKey("z_report_enabled")
        private val Z_MINUTE = intPreferencesKey("z_report_minute")
        private val Z_DONE_THROUGH = longPreferencesKey("z_report_done_through")
        private val Z_ENABLED_FROM = longPreferencesKey("z_report_enabled_from")
        private val Z_SUGGESTION_DISMISSED = booleanPreferencesKey("z_report_suggestion_dismissed")
        private val TARGET_AMOUNTS = stringPreferencesKey("target_amounts")
        private val TARGET_BASIS = stringPreferencesKey("target_basis")
        private val TARGET_DAYS = stringPreferencesKey("target_driving_days")
        private val TARGET_NOTIFIED = stringPreferencesKey("target_notified_month")
        private val TARGET_SUGGESTION_DISMISSED = booleanPreferencesKey("target_suggestion_dismissed")

        fun create(absolutePath: String): SettingsRepository =
            SettingsRepository(PreferenceDataStoreFactory.createWithPath(produceFile = { absolutePath.toPath() }))
    }
}

/** The weekly backup: [fileUri] is the file it overwrites (null = off). */
data class AutoBackupSettings(
    val fileUri: String? = null,
    val lastBackupEpochMillis: Long? = null,
    /** The last try couldn't write the file (moved, deleted, or no longer allowed). */
    val lastFailed: Boolean = false,
) {
    val enabled: Boolean get() = fileUri != null
}
