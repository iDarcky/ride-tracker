package app.ridetracker.shared.domain

import app.ridetracker.shared.data.AppDatabase
import app.ridetracker.shared.data.OdometerReadingEntity
import app.ridetracker.shared.data.VehicleDocumentEntity
import app.ridetracker.shared.data.VehicleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** Thrown when a reading would go backwards compared with the readings around its date. */
class OdometerOutOfOrderException : Exception()

/** Thrown when a reading is more than a car could drive since the previous one (likely a typo). */
class OdometerImplausibleException : Exception()

class VehicleRepository(database: AppDatabase) {
    private val dao = database.vehicleDao()

    fun observeVehicle(): Flow<VehicleEntity?> = dao.observeFirst()

    fun observeReadings(vehicleId: Long): Flow<List<OdometerReadingEntity>> = dao.observeReadings(vehicleId)

    suspend fun save(vehicle: VehicleEntity): Long =
        if (vehicle.id == 0L) dao.insert(vehicle) else vehicle.id.also { dao.update(vehicle) }

    /** Adds a reading; refuses one lower than an earlier reading or higher than a later one. */
    suspend fun addReading(vehicleId: Long, date: LocalDate, km: Long, existing: List<OdometerReadingEntity>, nowEpochMillis: Long) {
        val day = date.toEpochDays()
        val maxBefore = existing.filter { it.date <= day }.maxOfOrNull { it.km }
        val minAfter = existing.filter { it.date > day }.minOfOrNull { it.km }
        if ((maxBefore != null && km < maxBefore) || (minAfter != null && km > minAfter)) throw OdometerOutOfOrderException()
        val previous = existing.filter { it.date <= day }.maxByOrNull { it.km }
        if (previous != null && !VehicleMath.isPlausible(previous.km, previous.date, km, day)) throw OdometerImplausibleException()
        dao.insertReading(OdometerReadingEntity(vehicleId = vehicleId, date = day, km = km, createdAt = nowEpochMillis))
    }

    suspend fun deleteReading(id: Long) = dao.deleteReading(id)

    /** Puts a deleted reading back with its original id (Undo). */
    suspend fun restoreReading(reading: OdometerReadingEntity) {
        dao.insertReading(reading)
    }

    fun observeDocuments(vehicleId: Long): Flow<List<VehicleDocumentEntity>> = dao.observeDocuments(vehicleId)

    fun observeAllDocuments(): Flow<List<VehicleDocumentEntity>> = dao.observeAllDocuments()

    suspend fun getAllDocuments(): List<VehicleDocumentEntity> = dao.getAllDocuments()

    suspend fun getDocument(id: Long): VehicleDocumentEntity? = dao.getDocument(id)

    /** Adds or updates a document; a new expiry date starts its reminders again. */
    suspend fun saveDocument(document: VehicleDocumentEntity): Long {
        if (document.id == 0L) return dao.insertDocument(document)
        val old = dao.getDocument(document.id)
        dao.updateDocument(if (old != null && old.expiryDate != document.expiryDate) document.copy(notifiedStage = null) else document)
        return document.id
    }

    suspend fun deleteDocument(id: Long) = dao.deleteDocument(id)

    /** Puts a deleted document back with its original id (Undo). */
    suspend fun restoreDocument(document: VehicleDocumentEntity) {
        dao.insertDocument(document)
    }

    suspend fun markDocumentNotified(document: VehicleDocumentEntity, stage: Int) = dao.updateDocument(document.copy(notifiedStage = stage))
}
