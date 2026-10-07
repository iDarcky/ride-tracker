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
        SELECT e.id, e.platformId, e.amountMinor, e.date, e.note, e.createdAt,
               p.name AS platformName, p.colorArgb AS platformColorArgb
        FROM income_entry e JOIN platform p ON p.id = e.platformId
        WHERE e.date BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY e.date ASC, e.createdAt ASC
        """,
    )
    suspend fun getInRange(startEpochDay: Long, endEpochDay: Long): List<EntryWithPlatform>

    @Query(
        """
        SELECT e.id, e.platformId, e.amountMinor, e.date, e.note, e.createdAt,
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

    @Insert
    suspend fun insert(entry: IncomeEntryEntity): Long

    @Update
    suspend fun update(entry: IncomeEntryEntity)

    @Query("DELETE FROM income_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT e.id, e.platformId, e.amountMinor, e.date, e.note, e.createdAt,
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
