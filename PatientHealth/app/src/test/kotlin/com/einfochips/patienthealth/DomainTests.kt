package com.einfochips.patienthealth

import com.einfochips.patienthealth.domain.*
import com.einfochips.patienthealth.domain.DashboardCalculator.DAY_MS
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ValidationServiceTest {
    @Test fun validNames() = listOf("Anna", "Mary-Jane", "O'Brien", "José", "Li Wei", "A").forEach {
        assertTrue(it, ValidationService.validateName(it, "F").isValid)
    }
    @Test fun invalidNames() = listOf("", "   ", "J0hn", "Bob!", "a@b", "x".repeat(51)).forEach {
        assertFalse(it, ValidationService.validateName(it, "F").isValid)
    }
    @Test fun nameMessageIncludesField() = assertEquals("Last name is required", ValidationService.validateName("", "Last name").errorOrNull)

    @Test fun dobPastValid() = assertTrue(ValidationService.validateDateOfBirth(LocalDate.of(1990, 1, 1)).isValid)
    @Test fun dobTodayValid() { val t = LocalDate.now(); assertTrue(ValidationService.validateDateOfBirth(t, t).isValid) }
    @Test fun dobFutureInvalid() = assertEquals("Date of birth cannot be in the future", ValidationService.validateDateOfBirth(LocalDate.now().plusDays(1)).errorOrNull)
    @Test fun dobNullInvalid() = assertFalse(ValidationService.validateDateOfBirth(null).isValid)
    @Test fun dobTooOldInvalid() = assertFalse(ValidationService.validateDateOfBirth(LocalDate.now().minusYears(151)).isValid)

    @Test fun medicalIdValid() = listOf("ABC", "MRN-12345", "a1b2c3", "9".repeat(20)).forEach { assertTrue(it, ValidationService.validateMedicalId(it).isValid) }
    @Test fun medicalIdInvalid() = listOf("", "  ", "AB", "has space", "bad_id", "x!y", "9".repeat(21)).forEach { assertFalse(it, ValidationService.validateMedicalId(it).isValid) }
    @Test fun medicalIdMandatory() = assertEquals("Medical ID is required", ValidationService.validateMedicalId("").errorOrNull)

    @Test fun phone() {
        listOf("", "5551234", "+1 (555) 123-4567", "+919876543210").forEach { assertTrue(it, ValidationService.validatePhone(it).isValid) }
        listOf("123", "abcdefg", "1234567890123456", "555-CALL-NOW").forEach { assertFalse(it, ValidationService.validatePhone(it).isValid) }
    }
    @Test fun email() {
        listOf("", "a@b.co", "first.last+tag@example.com").forEach { assertTrue(it, ValidationService.validateEmail(it).isValid) }
        listOf("plain", "a@b", "@b.com", "a@.com", "a b@c.com", "a@b.c").forEach { assertFalse(it, ValidationService.validateEmail(it).isValid) }
    }

    @Test fun vitalBoundaries() = VitalType.entries.forEach { t ->
        val r = t.validRange
        assertTrue("$t lo", ValidationService.validateVital(r.start, t).isValid)
        assertTrue("$t hi", ValidationService.validateVital(r.endInclusive, t).isValid)
        assertFalse("$t below", ValidationService.validateVital(r.start - 0.1, t).isValid)
        assertFalse("$t above", ValidationService.validateVital(r.endInclusive + 0.1, t).isValid)
    }
    @Test fun vitalRejectsNaNInfinity() {
        assertFalse(ValidationService.validateVital(Double.NaN, VitalType.HEART_RATE).isValid)
        assertFalse(ValidationService.validateVital(Double.POSITIVE_INFINITY, VitalType.HEART_RATE).isValid)
    }
    @Test fun vitalMessage() = assertEquals("Heart Rate must be between 20 and 250 bpm", ValidationService.validateVital(300.0, VitalType.HEART_RATE).errorOrNull)
    @Test fun bpPair() {
        assertTrue(ValidationService.validateBloodPressurePair(120.0, 80.0).isValid)
        assertFalse(ValidationService.validateBloodPressurePair(80.0, 120.0).isValid)
        assertFalse(ValidationService.validateBloodPressurePair(100.0, 100.0).isValid)
    }
    @Test fun recordedAt() {
        val now = System.currentTimeMillis()
        assertTrue(ValidationService.validateRecordedAt(now - 3 * DAY_MS, now).isValid)
        assertFalse(ValidationService.validateRecordedAt(now + 3_600_000L, now).isValid)
    }
    @Test fun abnormal() {
        assertFalse(ValidationService.isAbnormal(72.0, VitalType.HEART_RATE))
        assertTrue(ValidationService.isAbnormal(120.0, VitalType.HEART_RATE))
        assertTrue(ValidationService.isAbnormal(50.0, VitalType.HEART_RATE))
        assertTrue(ValidationService.isAbnormal(38.5, VitalType.TEMPERATURE))
        assertTrue(ValidationService.isAbnormal(90.0, VitalType.SPO2))
        assertTrue(ValidationService.isAbnormal(8.0, VitalType.PAIN_LEVEL))
        assertFalse(ValidationService.isAbnormal(170.0, VitalType.HEIGHT))
    }
    @Test fun normalBoundariesInclusive() = VitalType.entries.forEach { t ->
        t.normalRange?.let {
            assertFalse(ValidationService.isAbnormal(it.start, t)); assertFalse(ValidationService.isAbnormal(it.endInclusive, t))
        }
    }
    @Test fun examinationTypesMatchRequirement() = assertEquals(
        listOf("Routine checkup", "Emergency", "Follow-up", "Consultation", "Diagnostic", "Preventive"), ExaminationType.entries.map { it.label })
}

class UnitConversionTest {
    @Test fun feetInchesToCm() {
        assertEquals(177.8, UnitConversion.cm(5.0, 10.0), 0.01)
        assertEquals(2.54, UnitConversion.cm(0.0, 1.0), 0.001)
    }
    @Test fun cmToFeetInches() { val (f, i) = UnitConversion.feetAndInches(177.8); assertEquals(5, f); assertEquals(10.0, i, 0.1) }
    @Test fun rollover() { val (f, i) = UnitConversion.feetAndInches(182.87); assertEquals(6, f); assertEquals(0.0, i, 0.001) }
    @Test fun heightRoundTrip() { var cm = 50.0; while (cm <= 220) { val (f, i) = UnitConversion.feetAndInches(cm); assertEquals(cm, UnitConversion.cm(f.toDouble(), i), 0.2); cm += 10 } }
    @Test fun weight() {
        assertEquals(70.0, UnitConversion.kg(154.324), 0.01)
        assertEquals(220.462, UnitConversion.lbs(100.0), 0.01)
        listOf(1.0, 45.5, 80.0, 250.0).forEach { assertEquals(it, UnitConversion.kg(UnitConversion.lbs(it)), 1e-9) }
    }
    @Test fun temperature() {
        assertEquals(37.0, UnitConversion.celsius(98.6), 0.01)
        assertEquals(0.0, UnitConversion.celsius(32.0), 1e-9)
        assertEquals(212.0, UnitConversion.fahrenheit(100.0), 1e-9)
        assertEquals(-40.0, UnitConversion.fahrenheit(-40.0), 1e-9)
    }
    @Test fun map() {
        assertEquals(93.3, UnitConversion.meanArterialPressure(120.0, 80.0), 0.001)
        assertEquals(70.0, UnitConversion.meanArterialPressure(90.0, 60.0), 0.001)
        assertEquals(106.7, UnitConversion.meanArterialPressure(140.0, 90.0), 0.001)
    }
    @Test fun rounding() { assertEquals(1.23, 1.2345.roundTo(2), 0.0); assertEquals(3.0, 2.5.roundTo(0), 0.0) }
}

class PatientFormStateTest {
    private fun filled() = PatientFormState(medicalId = "MRN-1", firstName = "Ann", lastName = "Lee", dob = LocalDate.of(1990, 5, 17))
    @Test fun emptyInvalid() = assertFalse(PatientFormState().isValid)
    @Test fun filledValid() = assertTrue(filled().isValid)
    @Test fun eachRequiredBlocks() {
        assertFalse(filled().copy(medicalId = "").isValid)
        assertFalse(filled().copy(firstName = "").isValid)
        assertFalse(filled().copy(lastName = "").isValid)
        assertFalse(filled().copy(dob = null).isValid)
    }
    @Test fun futureDobBlocks() { val s = filled().copy(dob = LocalDate.now().plusDays(30)); assertNotNull(s.dobError); assertFalse(s.isValid) }
    @Test fun badOptionalsBlock() { assertFalse(filled().copy(email = "nope").isValid); assertFalse(filled().copy(phone = "12").isValid) }
    @Test fun toPatientTrimsAndCopies() {
        val p = filled().copy(firstName = "  Ann ", gender = Gender.FEMALE, examType = ExaminationType.FOLLOW_UP).toPatient()
        assertEquals("Ann", p.firstName); assertEquals(Gender.FEMALE, p.gender); assertEquals(ExaminationType.FOLLOW_UP, p.examType)
        assertEquals(LocalDate.of(1990, 5, 17).toEpochDay(), p.dobEpochDay)
    }
    @Test fun consentTimestamping() {
        val granted = filled().copy(consentGiven = true).toPatient(now = 1000)
        assertTrue(granted.consentGiven); assertEquals(1000L, granted.consentAt)
        val unchanged = PatientFormState.from(granted).toPatient(granted, now = 2000)
        assertEquals("existing consent date kept", 1000L, unchanged.consentAt)
        val withdrawn = PatientFormState.from(granted).copy(consentGiven = false).toPatient(granted, now = 3000)
        assertFalse(withdrawn.consentGiven); assertNull(withdrawn.consentAt)
    }
    @Test fun editKeepsIdentity() {
        val p = filled().toPatient(); val edited = PatientFormState.from(p).copy(ethnicity = "X").toPatient(p)
        assertEquals(p.id, edited.id); assertEquals("X", edited.ethnicity)
    }
}

class VitalsEntryStateTest {
    @Test fun emptyCannotSave() = assertFalse(VitalsEntryState().canSave)
    @Test fun mapLive() {
        assertEquals(93.3, VitalsEntryState(systolic = "120", diastolic = "80").mapValue!!, 0.01)
        assertNull(VitalsEntryState(systolic = "120").mapValue)
    }
    @Test fun bpNeedsBoth() { val s = VitalsEntryState(systolic = "120"); assertNotNull(s.pairError); assertFalse(s.canSave) }
    @Test fun diastolicAboveSystolic() { val s = VitalsEntryState(systolic = "80", diastolic = "120"); assertNotNull(s.diastolicError); assertFalse(s.canSave) }
    @Test fun nonNumeric() { val s = VitalsEntryState(heartRate = "abc"); assertEquals("Enter a number", s.heartRateError); assertFalse(s.canSave) }
    @Test fun outOfRange() { val s = VitalsEntryState(spo2 = "101"); assertNotNull(s.spo2Error); assertFalse(s.canSave) }
    @Test fun decimalComma() = assertEquals(36.8, VitalsEntryState(temperature = "36,8").temperatureValueC!!, 0.001)
    @Test fun heightUnits() {
        assertEquals(170.0, VitalsEntryState(heightCm = "170").heightValueCm!!, 0.0)
        val f = VitalsEntryState(heightUnit = HeightUnit.FT_IN, heightFeet = "5", heightInches = "10")
        assertEquals(177.8, f.heightValueCm!!, 0.01)
        assertEquals(152.4, f.copy(heightInches = "").heightValueCm!!, 0.01)
        assertNull(f.copy(heightFeet = "").heightValueCm)
    }
    @Test fun weightUnits() {
        assertEquals(70.0, VitalsEntryState(weight = "70").weightValueKg!!, 0.0)
        assertEquals(70.0, VitalsEntryState(weightUnit = WeightUnit.LBS, weight = "154.32").weightValueKg!!, 0.01)
    }
    @Test fun temperatureUnits() {
        val s = VitalsEntryState(temperatureUnit = TemperatureUnit.FAHRENHEIT, temperature = "98.6")
        assertEquals(37.0, s.temperatureValueC!!, 0.01); assertNull(s.temperatureError)
        assertNotNull(s.copy(temperature = "200").temperatureError)
    }
    @Test fun futureDate() = assertFalse(VitalsEntryState(heartRate = "70", recordedAt = System.currentTimeMillis() + DAY_MS).canSave)
    @Test fun pastDate() = assertTrue(VitalsEntryState(heartRate = "70", recordedAt = System.currentTimeMillis() - 5 * DAY_MS).canSave)
    @Test fun painAlone() = assertTrue(VitalsEntryState(painLevel = 0).canSave)
    @Test fun symptomsAlone() = assertTrue(VitalsEntryState(symptoms = "Cough").canSave)
    @Test fun abnormalIndicator() {
        val s = VitalsEntryState()
        assertTrue(s.isAbnormal(130.0, VitalType.HEART_RATE)); assertFalse(s.isAbnormal(70.0, VitalType.HEART_RATE))
        assertFalse("invalid shows error not flag", s.isAbnormal(999.0, VitalType.HEART_RATE)); assertFalse(s.isAbnormal(null, VitalType.HEART_RATE))
    }
    @Test fun toRecordCanonical() {
        val r = VitalsEntryState(systolic = "120", diastolic = "80", heartRate = "72", weightUnit = WeightUnit.LBS, weight = "154.32",
            temperatureUnit = TemperatureUnit.FAHRENHEIT, temperature = "98.6", painLevel = 3).toRecord("p1")
        assertEquals("p1", r.patientId); assertEquals(120.0, r.systolic!!, 0.0); assertEquals(70.0, r.weightKg!!, 0.05)
        assertEquals(37.0, r.temperatureC!!, 0.05); assertEquals(3.0, r.painLevel!!, 0.0); assertNull(r.spo2)
    }
    @Test fun painNullWhenNotEntered() = assertNull(VitalsEntryState(heartRate = "70").toRecord("p").painLevel)
}

class VitalRecordAndDashboardTest {
    private val now = System.currentTimeMillis()
    private fun rec(daysAgo: Double, hr: Double?) = VitalRecord(patientId = "p", recordedAt = now - (daysAgo * 86_400_000).toLong(), heartRate = hr)

    @Test fun mapDerived() {
        assertEquals(93.3, VitalRecord(patientId = "p", recordedAt = 0, systolic = 120.0, diastolic = 80.0).map!!, 0.01)
        assertNull(VitalRecord(patientId = "p", recordedAt = 0, systolic = 120.0).map)
    }
    @Test fun valueForType() {
        val v = VitalRecord(patientId = "p", recordedAt = 0, systolic = 120.0, diastolic = 80.0, heartRate = 70.0, heightCm = 170.0, weightKg = 65.0,
            temperatureC = 36.8, respiratoryRate = 16.0, spo2 = 98.0, painLevel = 2.0)
        mapOf(VitalType.SYSTOLIC to 120.0, VitalType.DIASTOLIC to 80.0, VitalType.HEART_RATE to 70.0, VitalType.HEIGHT to 170.0, VitalType.WEIGHT to 65.0,
            VitalType.TEMPERATURE to 36.8, VitalType.RESPIRATORY_RATE to 16.0, VitalType.SPO2 to 98.0, VitalType.PAIN_LEVEL to 2.0)
            .forEach { (t, e) -> assertEquals("$t", e, v.value(t)!!, 0.0) }
        assertNotNull(v.value(VitalType.MAP)); assertNull(VitalRecord(patientId = "p", recordedAt = 0).value(VitalType.HEART_RATE))
    }
    @Test fun chartableExcludesHeight() = assertFalse(VitalType.chartable.contains(VitalType.HEIGHT))
    @Test fun recentSevenDaysSorted() =
        assertEquals(listOf(3.0, 1.0, 4.0), DashboardCalculator.recent(listOf(rec(1.0, 1.0), rec(10.0, 2.0), rec(6.9, 3.0), rec(0.1, 4.0)), now = now).map { it.heartRate })
    @Test fun recentExcludesFuture() = assertTrue(DashboardCalculator.recent(listOf(rec(-1.0, 1.0)), now = now).isEmpty())
    @Test fun pointsFlagAbnormal() {
        val pts = DashboardCalculator.points(listOf(rec(1.0, 70.0), rec(2.0, null), rec(3.0, 130.0)), VitalType.HEART_RATE)
        assertEquals(2, pts.size); assertEquals(1, pts.count { it.abnormal })
    }
    @Test fun stats() = assertEquals(DashboardCalculator.Stats(60.0, 100.0, 80.0, 100.0, 3),
        DashboardCalculator.stats(listOf(rec(3.0, 60.0), rec(2.0, 80.0), rec(1.0, 100.0)), VitalType.HEART_RATE))
    @Test fun statsNull() { assertNull(DashboardCalculator.stats(listOf(rec(1.0, null)), VitalType.HEART_RATE)); assertNull(DashboardCalculator.stats(emptyList(), VitalType.SPO2)) }
}
