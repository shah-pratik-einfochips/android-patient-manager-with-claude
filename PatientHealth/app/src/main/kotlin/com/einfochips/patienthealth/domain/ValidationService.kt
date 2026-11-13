package com.einfochips.patienthealth.domain

import java.time.LocalDate

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val message: String) : ValidationResult

    val isValid: Boolean get() = this is Valid
    val errorOrNull: String? get() = (this as? Invalid)?.message
}

object ValidationService {
    private val emailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    /** Required person name: letters, spaces, hyphens, apostrophes; max 50 chars. */
    fun validateName(value: String, field: String): ValidationResult {
        val v = value.trim()
        return when {
            v.isEmpty() -> ValidationResult.Invalid("$field is required")
            v.length > 50 -> ValidationResult.Invalid("$field must be 50 characters or fewer")
            !v.all { it.isLetter() || it == ' ' || it == '-' || it == '\'' } -> ValidationResult.Invalid("$field contains invalid characters")
            else -> ValidationResult.Valid
        }
    }

    /** DOB must not be in the future nor more than 150 years ago. */
    fun validateDateOfBirth(dob: LocalDate?, today: LocalDate = LocalDate.now()): ValidationResult = when {
        dob == null -> ValidationResult.Invalid("Date of birth is required")
        dob.isAfter(today) -> ValidationResult.Invalid("Date of birth cannot be in the future")
        dob.isBefore(today.minusYears(150)) -> ValidationResult.Invalid("Date of birth is too far in the past")
        else -> ValidationResult.Valid
    }

    /** Mandatory medical ID: 3-20 letters, digits or hyphens. */
    fun validateMedicalId(value: String): ValidationResult {
        val v = value.trim()
        return when {
            v.isEmpty() -> ValidationResult.Invalid("Medical ID is required")
            v.length !in 3..20 -> ValidationResult.Invalid("Medical ID must be 3–20 characters")
            !v.all { it.isLetterOrDigit() || it == '-' } -> ValidationResult.Invalid("Medical ID may only contain letters, numbers and hyphens")
            else -> ValidationResult.Valid
        }
    }

    /** Optional phone: 7-15 digits with optional + - ( ) and spaces. */
    fun validatePhone(value: String): ValidationResult {
        val v = value.trim()
        if (v.isEmpty()) return ValidationResult.Valid
        if (!v.all { it.isDigit() || it in "+-() " }) return ValidationResult.Invalid("Phone number contains invalid characters")
        return if (v.count { it.isDigit() } in 7..15) ValidationResult.Valid else ValidationResult.Invalid("Phone number must have 7–15 digits")
    }

    /** Optional email using a pragmatic pattern. */
    fun validateEmail(value: String): ValidationResult {
        val v = value.trim()
        return if (v.isEmpty() || emailRegex.matches(v)) ValidationResult.Valid else ValidationResult.Invalid("Enter a valid email address")
    }

    /** Vital value (canonical units) must be finite and inside the hard limits. */
    fun validateVital(value: Double, type: VitalType): ValidationResult {
        val r = type.validRange
        return if (value.isFinite() && value in r) ValidationResult.Valid
        else ValidationResult.Invalid("${type.title} must be between ${fmt(r.start)} and ${fmt(r.endInclusive)} ${type.unit}")
    }

    fun validateBloodPressurePair(systolic: Double, diastolic: Double): ValidationResult =
        if (diastolic < systolic) ValidationResult.Valid else ValidationResult.Invalid("Diastolic must be lower than systolic")

    /** Measurement time may be in the past but not in the future (60 s clock-skew allowance). */
    fun validateRecordedAt(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): ValidationResult =
        if (epochMillis <= nowMillis + 60_000) ValidationResult.Valid else ValidationResult.Invalid("Measurement date cannot be in the future")

    /** True when the value is outside the type's normal range (types without a range are never abnormal). */
    fun isAbnormal(value: Double, type: VitalType): Boolean = type.normalRange?.let { value !in it } ?: false

    private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}
