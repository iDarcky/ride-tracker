package app.ridetracker.shared.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {
    @Query("SELECT * FROM platform ORDER BY archived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<PlatformEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM platform")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(platform: PlatformEntity): Long

    @Update
    suspend fun update(platform: PlatformEntity)

    @Query("SELECT * FROM platform ORDER BY id")
    suspend fun getAll(): List<PlatformEntity>

    @Insert
    suspend fun insertAll(platforms: List<PlatformEntity>)

    @Query("DELETE FROM platform")
    suspend fun deleteAll()
}

@Dao
interface IncomeEntryDao {
    @Query(
        """
        SELECT e.id, e.platformId, e.amountMinor, e.date, e.note, e.createdAt, e.source,
               p.name AS platformName, p.colorArgb AS platformColorArgb
        FROM income_entry e JOIN platform p ON p.id = e.platformId
        WHERE e.date BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY e.date ASC, e.createdAt ASC
        """,
    )
    suspend fun getInRange(startEpochDay: Long, endEpochDay: Long): List<EntryWithPlatform>

    @Query(
        """
        SELECT e.id, e.platformId, e.amountMinor, e.date, e.note, e.createdAt, e.source,
               p.name AS platformName, p.colorArgb AS platformColorArgb
        FROM income_entry e JOIN platform p ON p.id = e.platformId
        WHERE e.date BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY e.date DESC, e.createdAt DESC
        """,
    )
    fun observeInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<EntryWithPlatform>>

    @Query(
        """
        SELECT p.id AS platformId, p.name AS name, p.colorArgb AS colorArgb, SUM(e.amountMinor) AS totalMinor
        FROM income_entry e JOIN platform p ON p.id = e.platformId
        WHERE e.date BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY p.id
        ORDER BY totalMinor DESC
        """,
    )
    fun observeTotalsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<PlatformTotal>>

    @Query("SELECT * FROM income_entry WHERE id = :id")
    suspend fun getById(id: Long): IncomeEntryEntity?

    @Query("SELECT * FROM income_entry WHERE date BETWEEN :startEpochDay AND :endEpochDay ORDER BY date, id")
    fun observeDetailsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<IncomeEntryEntity>>

    @Query(
        """
        SELECT l.entryId, e.platformId, e.date, l.kind, l.amountMinor
        FROM income_line l JOIN income_entry e ON e.id = l.entryId
        WHERE e.date BETWEEN :startEpochDay AND :endEpochDay
        """,
    )
    fun observeLinesInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<LineInRange>>

    /** The earliest day with any income, expense or trip (for the "All" period); null when there is no data. */
    @Query(
        """
        SELECT MIN(d) FROM (
            SELECT MIN(date) AS d FROM income_entry UNION ALL SELECT MIN(date) FROM expense UNION ALL SELECT MIN(date) FROM trip
        )
        """,
    )
    suspend fun firstDay(): Long?

    @Query("SELECT * FROM income_entry WHERE platformId = :platformId AND date = :epochDay ORDER BY id")
    suspend fun getForDay(platformId: Long, epochDay: Long): List<IncomeEntryEntity>

    @Insert
    suspend fun insert(entry: IncomeEntryEntity): Long

    @Update
    suspend fun update(entry: IncomeEntryEntity)

    @Query("DELETE FROM income_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT e.id, e.platformId, e.amountMinor, e.date, e.note, e.createdAt, e.source,
               p.name AS platformName, p.colorArgb AS platformColorArgb
        FROM income_entry e JOIN platform p ON p.id = e.platformId
        ORDER BY e.date DESC, e.createdAt DESC
        """,
    )
    fun observeAll(): Flow<List<EntryWithPlatform>>

    @Query("SELECT * FROM income_entry ORDER BY id")
    suspend fun getAll(): List<IncomeEntryEntity>

    @Insert
    suspend fun insertAll(entries: List<IncomeEntryEntity>)

    @Query("DELETE FROM income_entry")
    suspend fun deleteAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense WHERE date BETWEEN :startEpochDay AND :endEpochDay ORDER BY date ASC, createdAt ASC")
    suspend fun getInRange(startEpochDay: Long, endEpochDay: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expense WHERE date BETWEEN :startEpochDay AND :endEpochDay ORDER BY date DESC, createdAt DESC")
    fun observeInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM expense)")
    fun observeAny(): Flow<Boolean>

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expense WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM expense ORDER BY id")
    suspend fun getAll(): List<ExpenseEntity>

    @Insert
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expense")
    suspend fun deleteAll()
}

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicle ORDER BY id LIMIT 1")
    fun observeFirst(): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicle ORDER BY id")
    suspend fun getAll(): List<VehicleEntity>

    @Insert
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Insert
    suspend fun insertAll(vehicles: List<VehicleEntity>)

    @Query("DELETE FROM vehicle")
    suspend fun deleteAll()

    @Query("SELECT * FROM odometer_reading WHERE vehicleId = :vehicleId ORDER BY date DESC, km DESC")
    fun observeReadings(vehicleId: Long): Flow<List<OdometerReadingEntity>>

    @Query("SELECT * FROM odometer_reading ORDER BY id")
    suspend fun getAllReadings(): List<OdometerReadingEntity>

    @Insert
    suspend fun insertReading(reading: OdometerReadingEntity): Long

    @Insert
    suspend fun insertReadings(readings: List<OdometerReadingEntity>)

    @Query("DELETE FROM odometer_reading WHERE id = :id")
    suspend fun deleteReading(id: Long)

    @Query("DELETE FROM odometer_reading")
    suspend fun deleteAllReadings()
}

@Dao
interface RecurringExpenseDao {
    @Query("SELECT * FROM recurring_expense ORDER BY nextDueDate ASC, id ASC")
    fun observeAll(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expense ORDER BY id")
    suspend fun getAll(): List<RecurringExpenseEntity>

    @Query("SELECT * FROM recurring_expense WHERE id = :id")
    suspend fun getById(id: Long): RecurringExpenseEntity?

    @Insert
    suspend fun insert(rule: RecurringExpenseEntity): Long

    @Update
    suspend fun update(rule: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expense WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(rules: List<RecurringExpenseEntity>)

    @Query("DELETE FROM recurring_expense")
    suspend fun deleteAll()
}

@Dao
interface ImportDao {
    @Query("SELECT * FROM income_line WHERE entryId = :entryId ORDER BY id")
    suspend fun getLines(entryId: Long): List<IncomeLineEntity>

    @Query("SELECT * FROM income_line ORDER BY id")
    suspend fun getAllLines(): List<IncomeLineEntity>

    @Insert
    suspend fun insertLines(lines: List<IncomeLineEntity>)

    @Query("SELECT * FROM income_entry WHERE platformId = :platformId AND date BETWEEN :startEpochDay AND :endEpochDay")
    suspend fun getEntries(platformId: Long, startEpochDay: Long, endEpochDay: Long): List<IncomeEntryEntity>

    @Query("DELETE FROM income_entry WHERE source = 'estimate' AND platformId = :platformId AND date BETWEEN :startEpochDay AND :endEpochDay")
    suspend fun deleteEstimates(platformId: Long, startEpochDay: Long, endEpochDay: Long)

    @Query("SELECT platformId, date FROM income_entry WHERE source = 'estimate'")
    suspend fun getEstimateDays(): List<PlatformDay>

    @Query("SELECT DISTINCT platformId, date FROM trip")
    suspend fun getTripDays(): List<PlatformDay>

    @Query(
        """
        SELECT date, SUM(fareMinor) AS totalMinor FROM trip
        WHERE platformId = :platformId AND date BETWEEN :startEpochDay AND :endEpochDay GROUP BY date
        """,
    )
    suspend fun getTripFaresByDay(platformId: Long, startEpochDay: Long, endEpochDay: Long): List<DayTotal>

    /** Removes older online-time totals for the same period, so a newer screenshot replaces them. */
    @Query(
        """
        DELETE FROM period_summary WHERE platformId = :platformId AND periodStart = :startEpochDay AND periodEnd = :endEpochDay
        AND onlineMinutes IS NOT NULL AND earningsMinor IS NULL AND grossFareMinor IS NULL
        """,
    )
    suspend fun deleteOnlineTime(platformId: Long, startEpochDay: Long, endEpochDay: Long)

    @Query("SELECT * FROM period_summary WHERE platformId = :platformId AND periodStart = :startEpochDay AND periodEnd = :endEpochDay")
    suspend fun getSummaries(platformId: Long, startEpochDay: Long, endEpochDay: Long): List<PeriodSummaryEntity>

    /** Fare and commission totals of a platform's imported breakdowns, for its usual commission share. */
    @Query(
        """
        SELECT l.kind, SUM(l.amountMinor) AS totalMinor FROM income_line l JOIN income_entry e ON e.id = l.entryId
        WHERE e.platformId = :platformId AND l.kind IN ('fare', 'commission') GROUP BY l.kind
        """,
    )
    suspend fun getFareAndCommission(platformId: Long): List<KindTotal>

    @Query("SELECT * FROM income_line WHERE kind = :kind AND label IS NOT NULL")
    suspend fun getLinesOfKind(kind: String): List<IncomeLineEntity>

    @Query("UPDATE income_line SET kind = :kind WHERE id = :id")
    suspend fun setLineKind(id: Long, kind: String)

    @Query("DELETE FROM income_line WHERE entryId = :entryId")
    suspend fun deleteLines(entryId: Long)

    @Query("DELETE FROM income_line")
    suspend fun deleteAllLines()

    @Query("SELECT * FROM import_batch ORDER BY importedAt DESC, id DESC")
    fun observeBatches(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batch ORDER BY id")
    suspend fun getAllBatches(): List<ImportBatchEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM import_batch WHERE fileHash = :fileHash)")
    suspend fun hasFile(fileHash: String): Boolean

    @Insert
    suspend fun insertBatch(batch: ImportBatchEntity): Long

    @Insert
    suspend fun insertBatches(batches: List<ImportBatchEntity>)

    @Query("DELETE FROM import_batch")
    suspend fun deleteAllBatches()

    @Query(
        """
        SELECT t.id, t.platformId, p.name AS platformName, p.colorArgb AS platformColorArgb, t.date, t.startMinute,
               t.fareMinor, t.paymentMethod, t.distanceMeters, t.durationSeconds, b.kind AS importKind
        FROM trip t JOIN platform p ON p.id = t.platformId JOIN import_batch b ON b.id = t.importBatchId
        WHERE t.date BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY t.date DESC, t.startMinute DESC
        """,
    )
    fun observeTrips(startEpochDay: Long, endEpochDay: Long): Flow<List<TripWithPlatform>>

    @Query(
        """
        SELECT t.id, t.platformId, p.name AS platformName, p.colorArgb AS platformColorArgb, t.date, t.startMinute,
               t.fareMinor, t.paymentMethod, t.distanceMeters, t.durationSeconds, b.kind AS importKind
        FROM trip t JOIN platform p ON p.id = t.platformId JOIN import_batch b ON b.id = t.importBatchId
        WHERE t.id = :id
        """,
    )
    suspend fun getTrip(id: Long): TripWithPlatform?

    /** Days that have trips, newest first (for the month picker). */
    @Query("SELECT DISTINCT date FROM trip ORDER BY date DESC")
    fun observeTripDays(): Flow<List<Long>>

    @Query("SELECT * FROM trip ORDER BY id")
    suspend fun getAllTrips(): List<TripEntity>

    @Query("SELECT externalId FROM trip WHERE platformId = :platformId AND externalId IN (:externalIds)")
    suspend fun existingTripIds(platformId: Long, externalIds: List<String>): List<String>

    @Insert
    suspend fun insertTrips(trips: List<TripEntity>)

    @Query("DELETE FROM trip")
    suspend fun deleteAllTrips()

    @Query("SELECT * FROM period_summary ORDER BY id")
    suspend fun getAllSummaries(): List<PeriodSummaryEntity>

    @Query("SELECT * FROM period_summary WHERE periodEnd >= :startEpochDay AND periodStart <= :endEpochDay ORDER BY periodStart")
    fun observeSummaries(startEpochDay: Long, endEpochDay: Long): Flow<List<PeriodSummaryEntity>>

    @Insert
    suspend fun insertSummaries(summaries: List<PeriodSummaryEntity>)

    @Query("DELETE FROM period_summary")
    suspend fun deleteAllSummaries()
}
