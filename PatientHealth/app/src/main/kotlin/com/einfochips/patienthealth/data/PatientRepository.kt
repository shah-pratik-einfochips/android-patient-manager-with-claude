package com.einfochips.patienthealth.data

import com.einfochips.patienthealth.domain.AuditEntry
import com.einfochips.patienthealth.domain.Patient
import com.einfochips.patienthealth.domain.VitalRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

class ConsentRequiredException : Exception("Patient consent is required before data can be uploaded.")
class NotSyncedException : Exception("This patient has not been uploaded yet.")
class DuplicateMedicalIdException : Exception("A patient with this medical ID already exists.")

class PatientRepository(
    private val patients: PatientDao,
    private val vitals: VitalDao,
    private val audit: AuditDao,
    private val fhir: FhirApi,
) {
    fun observePatients(): Flow<List<Patient>> = patients.observeAll()
    fun observePatient(id: String): Flow<Patient?> = patients.observe(id)
    fun observeVitalsSince(patientId: String, since: Long): Flow<List<VitalRecord>> = vitals.observeSince(patientId, since)
    fun observeAudit(limit: Int = 50): Flow<List<AuditEntry>> = audit.observeRecent(limit)
    suspend fun getPatient(id: String): Patient? = patients.get(id)
    suspend fun allVitals(patientId: String): List<VitalRecord> = vitals.all(patientId)

    suspend fun log(action: String, detail: String = "") = audit.insert(AuditEntry(action = action, detail = detail))

    /** Inserts or updates a patient; medical IDs must stay unique. */
    suspend fun savePatient(patient: Patient, isNew: Boolean) {
        if (patients.countDuplicates(patient.medicalId, patient.id) > 0) throw DuplicateMedicalIdException()
        patients.upsert(patient)
        log(if (isNew) "patient.create" else "patient.update", patient.id)
    }

    suspend fun deletePatient(id: String) {
        patients.delete(id)
        log("patient.delete", id)
    }

    suspend fun addVitals(record: VitalRecord) {
        vitals.insert(record)
        log("vitals.create", record.patientId)
    }

    /** Uploads the patient and any not-yet-uploaded vitals. Requires consent. */
    suspend fun push(patientId: String) {
        val local = patients.get(patientId) ?: return
        if (!local.consentGiven) throw ConsentRequiredException()
        val stored = fhir.upsertPatient(FhirMapper.patientResource(local))
        val serverId = stored["id"]?.jsonPrimitive?.content ?: throw FhirException.Decoding()
        patients.upsert(local.copy(fhirId = serverId))
        for (record in vitals.unsynced(patientId)) {
            val ids = FhirMapper.observations(record, serverId).map { fhir.createObservation(it) }
            vitals.setFhirIds(record.id, ids.joinToString(","))
        }
        log("sync.push", patientId)
    }

    /** Downloads demographics and any vitals not already stored (matched by timestamp). */
    suspend fun pull(patientId: String) {
        val local = patients.get(patientId) ?: return
        val serverId = local.fhirId ?: throw NotSyncedException()
        patients.upsert(FhirMapper.applyPatient(fhir.fetchPatient(serverId), local))

        val grouped = fhir.fetchObservations(serverId).groupBy { it["effectiveDateTime"]?.jsonPrimitive?.content.orEmpty() }
        for ((stamp, group) in grouped) {
            val time = runCatching { Instant.parse(stamp).toEpochMilli() }.getOrNull() ?: continue
            if (vitals.countNear(patientId, time) > 0) continue
            val record = FhirMapper.vitalRecord(group, patientId, time) ?: continue
            vitals.insert(record.copy(fhirIds = group.mapNotNull { it["id"]?.jsonPrimitive?.content }.joinToString(",")))
        }
        log("sync.pull", patientId)
    }

    /** Pushes every consented patient; used by background sync. Returns the number that failed. */
    suspend fun pushAllConsented(): Int {
        var failures = 0
        for (p in patients.consented()) runCatching { push(p.id) }.onFailure { failures++ }
        return failures
    }
}
