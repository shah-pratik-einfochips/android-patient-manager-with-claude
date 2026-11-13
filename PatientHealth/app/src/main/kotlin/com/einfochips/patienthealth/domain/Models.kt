package com.einfochips.patienthealth.domain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "patients", indices = [Index("medicalId", unique = true)])
data class Patient(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val medicalId: String,
    val firstName: String,
    val lastName: String,
    val dobEpochDay: Long,
    val gender: Gender = Gender.UNKNOWN,
    val ethnicity: String = "",
    val phone: String = "",
    val email: String = "",
    val bloodType: BloodType = BloodType.UNKNOWN,
    val allergies: String = "",
    val medications: String = "",
    val conditions: String = "",
    val examPurpose: String = "",
    val examType: ExaminationType = ExaminationType.ROUTINE_CHECKUP,
    val performedBy: String = "",
    val referredBy: String = "",
    val consentGiven: Boolean = false,
    val consentAt: Long? = null,
    val fhirId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val fullName: String get() = "$firstName $lastName"
}

@Entity(
    tableName = "vitals",
    foreignKeys = [ForeignKey(Patient::class, ["id"], ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["patientId", "recordedAt"])],
)
data class VitalRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val recordedAt: Long,
    val systolic: Double? = null,
    val diastolic: Double? = null,
    val heartRate: Double? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val temperatureC: Double? = null,
    val respiratoryRate: Double? = null,
    val spo2: Double? = null,
    val painLevel: Double? = null,
    val symptoms: String = "",
    /** Comma-separated server observation ids; empty means not yet uploaded. */
    val fhirIds: String = "",
) {
    /** Mean arterial pressure, derived so it can never disagree with systolic/diastolic. */
    val map: Double?
        get() = if (systolic != null && diastolic != null) UnitConversion.meanArterialPressure(systolic, diastolic) else null

    /** Value for a vital type in canonical units, or null if not recorded. */
    fun value(type: VitalType): Double? = when (type) {
        VitalType.SYSTOLIC -> systolic
        VitalType.DIASTOLIC -> diastolic
        VitalType.MAP -> map
        VitalType.HEART_RATE -> heartRate
        VitalType.HEIGHT -> heightCm
        VitalType.WEIGHT -> weightKg
        VitalType.TEMPERATURE -> temperatureC
        VitalType.RESPIRATORY_RATE -> respiratoryRate
        VitalType.SPO2 -> spo2
        VitalType.PAIN_LEVEL -> painLevel
    }
}

/** Audit entries hold action names and identifiers only - never clinical values. */
@Entity(tableName = "audit_log")
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val detail: String = "",
)
