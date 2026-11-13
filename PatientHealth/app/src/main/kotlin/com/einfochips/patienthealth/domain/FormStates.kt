package com.einfochips.patienthealth.domain

import java.time.LocalDate

/** Immutable state of the patient form; error properties are derived so the UI never holds stale validation. */
data class PatientFormState(
    val medicalId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val dob: LocalDate? = null,
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
    val submitted: Boolean = false,
) {
    val medicalIdError get() = ValidationService.validateMedicalId(medicalId).errorOrNull
    val firstNameError get() = ValidationService.validateName(firstName, "First name").errorOrNull
    val lastNameError get() = ValidationService.validateName(lastName, "Last name").errorOrNull
    val dobError get() = ValidationService.validateDateOfBirth(dob).errorOrNull
    val phoneError get() = ValidationService.validatePhone(phone).errorOrNull
    val emailError get() = ValidationService.validateEmail(email).errorOrNull

    val isValid: Boolean
        get() = listOf(medicalIdError, firstNameError, lastNameError, dobError, phoneError, emailError).all { it == null }

    /** Applies the form onto [base] (or a new patient), stamping/clearing the consent date on change. */
    fun toPatient(base: Patient? = null, now: Long = System.currentTimeMillis()): Patient {
        val p = base ?: Patient(medicalId = "", firstName = "", lastName = "", dobEpochDay = 0)
        return p.copy(
            medicalId = medicalId.trim(), firstName = firstName.trim(), lastName = lastName.trim(),
            dobEpochDay = dob?.toEpochDay() ?: p.dobEpochDay, gender = gender, ethnicity = ethnicity,
            phone = phone, email = email, bloodType = bloodType, allergies = allergies,
            medications = medications, conditions = conditions, examPurpose = examPurpose,
            examType = examType, performedBy = performedBy, referredBy = referredBy,
            consentGiven = consentGiven,
            consentAt = when {
                !consentGiven -> null
                p.consentGiven -> p.consentAt
                else -> now
            },
            updatedAt = now,
        )
    }

    companion object {
        fun from(p: Patient) = PatientFormState(
            p.medicalId, p.firstName, p.lastName, LocalDate.ofEpochDay(p.dobEpochDay), p.gender, p.ethnicity,
            p.phone, p.email, p.bloodType, p.allergies, p.medications, p.conditions, p.examPurpose,
            p.examType, p.performedBy, p.referredBy, p.consentGiven,
        )
    }
}

/** Immutable state of the vitals entry form with unit-aware canonical conversions. */
data class VitalsEntryState(
    val recordedAt: Long = System.currentTimeMillis(),
    val systolic: String = "",
    val diastolic: String = "",
    val heartRate: String = "",
    val heightUnit: HeightUnit = HeightUnit.CM,
    val heightCm: String = "",
    val heightFeet: String = "",
    val heightInches: String = "",
    val weightUnit: WeightUnit = WeightUnit.KG,
    val weight: String = "",
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val temperature: String = "",
    val respiratoryRate: String = "",
    val spo2: String = "",
    val painLevel: Int? = null,
    val symptoms: String = "",
) {
    private fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    val systolicValue get() = num(systolic)
    val diastolicValue get() = num(diastolic)
    val heartRateValue get() = num(heartRate)
    val respiratoryRateValue get() = num(respiratoryRate)
    val spo2Value get() = num(spo2)

    val heightValueCm: Double?
        get() = when (heightUnit) {
            HeightUnit.CM -> num(heightCm)
            HeightUnit.FT_IN -> num(heightFeet)?.let { UnitConversion.cm(it, num(heightInches) ?: 0.0) }
        }
    val weightValueKg: Double? get() = num(weight)?.let { if (weightUnit == WeightUnit.KG) it else UnitConversion.kg(it) }
    val temperatureValueC: Double?
        get() = num(temperature)?.let { if (temperatureUnit == TemperatureUnit.CELSIUS) it else UnitConversion.celsius(it) }

    /** Live-calculated MAP; null until both pressures parse. */
    val mapValue: Double?
        get() {
            val s = systolicValue ?: return null
            val d = diastolicValue ?: return null
            return UnitConversion.meanArterialPressure(s, d)
        }

    private fun fieldError(raw: String, value: Double?, type: VitalType): String? = when {
        raw.isBlank() -> null
        value == null -> "Enter a number"
        else -> ValidationService.validateVital(value, type).errorOrNull
    }

    val systolicError get() = fieldError(systolic, systolicValue, VitalType.SYSTOLIC)
    val diastolicError: String?
        get() {
            fieldError(diastolic, diastolicValue, VitalType.DIASTOLIC)?.let { return it }
            val s = systolicValue; val d = diastolicValue
            return if (s != null && d != null) ValidationService.validateBloodPressurePair(s, d).errorOrNull else null
        }
    val pairError get() = if (systolic.isBlank() != diastolic.isBlank()) "Enter both systolic and diastolic" else null
    val heartRateError get() = fieldError(heartRate, heartRateValue, VitalType.HEART_RATE)
    val heightError get() = fieldError(if (heightUnit == HeightUnit.CM) heightCm else heightFeet, heightValueCm, VitalType.HEIGHT)
    val weightError get() = fieldError(weight, weightValueKg, VitalType.WEIGHT)
    val temperatureError get() = fieldError(temperature, temperatureValueC, VitalType.TEMPERATURE)
    val respiratoryError get() = fieldError(respiratoryRate, respiratoryRateValue, VitalType.RESPIRATORY_RATE)
    val spo2Error get() = fieldError(spo2, spo2Value, VitalType.SPO2)
    val dateError get() = ValidationService.validateRecordedAt(recordedAt).errorOrNull

    val hasAnyValue: Boolean
        get() = listOf(systolicValue, heartRateValue, heightValueCm, weightValueKg, temperatureValueC, respiratoryRateValue, spo2Value)
            .any { it != null } || painLevel != null || symptoms.isNotBlank()

    val canSave: Boolean
        get() = hasAnyValue && listOf(systolicError, diastolicError, pairError, heartRateError, heightError, weightError,
            temperatureError, respiratoryError, spo2Error, dateError).all { it == null }

    /** True when a valid value sits outside the normal range (invalid values show an error instead). */
    fun isAbnormal(value: Double?, type: VitalType): Boolean =
        value != null && ValidationService.validateVital(value, type).isValid && ValidationService.isAbnormal(value, type)

    fun toRecord(patientId: String): VitalRecord = VitalRecord(
        patientId = patientId, recordedAt = recordedAt, systolic = systolicValue, diastolic = diastolicValue,
        heartRate = heartRateValue, heightCm = heightValueCm?.roundTo(1), weightKg = weightValueKg?.roundTo(1),
        temperatureC = temperatureValueC?.roundTo(1), respiratoryRate = respiratoryRateValue, spo2 = spo2Value,
        painLevel = painLevel?.toDouble(), symptoms = symptoms,
    )
}

/** Dashboard calculations over a patient's vitals. */
object DashboardCalculator {
    data class Stats(val min: Double, val max: Double, val average: Double, val latest: Double, val count: Int)
    data class Point(val time: Long, val value: Double, val abnormal: Boolean)

    private const val DAY_MS = 86_400_000L

    /** Records from the last [days] days up to [now], oldest first. */
    fun recent(records: List<VitalRecord>, days: Int = 7, now: Long = System.currentTimeMillis()): List<VitalRecord> =
        records.filter { it.recordedAt in (now - days * DAY_MS)..now }.sortedBy { it.recordedAt }

    fun points(records: List<VitalRecord>, type: VitalType): List<Point> =
        records.mapNotNull { r -> r.value(type)?.let { Point(r.recordedAt, it, ValidationService.isAbnormal(it, type)) } }

    fun stats(records: List<VitalRecord>, type: VitalType): Stats? {
        val pts = points(records, type)
        val latest = pts.maxByOrNull { it.time } ?: return null
        val v = pts.map { it.value }
        return Stats(v.min(), v.max(), v.average().roundTo(1), latest.value, v.size)
    }
}
