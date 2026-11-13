package com.einfochips.patienthealth.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.einfochips.patienthealth.domain.AuditEntry
import com.einfochips.patienthealth.domain.Patient
import com.einfochips.patienthealth.domain.VitalRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY lastName, firstName")
    fun observeAll(): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE id = :id")
    fun observe(id: String): Flow<Patient?>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun get(id: String): Patient?

    @Query("SELECT * FROM patients WHERE consentGiven = 1")
    suspend fun consented(): List<Patient>

    @Query("SELECT COUNT(*) FROM patients WHERE medicalId = :medicalId AND id != :excludingId")
    suspend fun countDuplicates(medicalId: String, excludingId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(patient: Patient)

    @Query("DELETE FROM patients WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VitalDao {
    @Query("SELECT * FROM vitals WHERE patientId = :patientId AND recordedAt >= :since ORDER BY recordedAt")
    fun observeSince(patientId: String, since: Long): Flow<List<VitalRecord>>

    @Query("SELECT * FROM vitals WHERE patientId = :patientId ORDER BY recordedAt")
    suspend fun all(patientId: String): List<VitalRecord>

    @Query("SELECT * FROM vitals WHERE patientId = :patientId AND fhirIds = ''")
    suspend fun unsynced(patientId: String): List<VitalRecord>

    @Query("SELECT COUNT(*) FROM vitals WHERE patientId = :patientId AND ABS(recordedAt - :time) < 1000")
    suspend fun countNear(patientId: String, time: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: VitalRecord)

    @Query("UPDATE vitals SET fhirIds = :ids WHERE id = :id")
    suspend fun setFhirIds(id: String, ids: String)
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditEntry)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditEntry>>

    @Query("SELECT COUNT(*) FROM audit_log")
    fun observeCount(): Flow<Int>
}

@Database(entities = [Patient::class, VitalRecord::class, AuditEntry::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun vitalDao(): VitalDao
    abstract fun auditDao(): AuditDao

    companion object {
        const val NAME = "patient_health.db"

        /** Add each schema upgrade here (never use destructive migration for patient data). */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
