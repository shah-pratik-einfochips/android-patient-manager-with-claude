package com.einfochips.patienthealth.domain

enum class Gender(val title: String, val fhir: String) {
    MALE("Male", "male"), FEMALE("Female", "female"), OTHER("Other", "other"), UNKNOWN("Unknown", "unknown");

    companion object {
        /** Parses a FHIR gender code, falling back to UNKNOWN. */
        fun fromFhir(code: String?): Gender = entries.firstOrNull { it.fhir == code } ?: UNKNOWN
    }
}

enum class BloodType(val label: String) {
    UNKNOWN("Unknown"), A_POS("A+"), A_NEG("A-"), B_POS("B+"), B_NEG("B-"),
    AB_POS("AB+"), AB_NEG("AB-"), O_POS("O+"), O_NEG("O-")
}

enum class ExaminationType(val label: String) {
    ROUTINE_CHECKUP("Routine checkup"), EMERGENCY("Emergency"), FOLLOW_UP("Follow-up"),
    CONSULTATION("Consultation"), DIAGNOSTIC("Diagnostic"), PREVENTIVE("Preventive")
}

enum class HeightUnit(val label: String) { CM("cm"), FT_IN("ft/in") }
enum class WeightUnit(val label: String) { KG("kg"), LBS("lbs") }
enum class TemperatureUnit(val label: String) { CELSIUS("°C"), FAHRENHEIT("°F") }

enum class VitalType(
    val title: String,
    val unit: String,
    val validRange: ClosedFloatingPointRange<Double>,
    val normalRange: ClosedFloatingPointRange<Double>?,
) {
    SYSTOLIC("Systolic BP", "mmHg", 50.0..260.0, 90.0..120.0),
    DIASTOLIC("Diastolic BP", "mmHg", 30.0..160.0, 60.0..80.0),
    MAP("Mean Arterial Pressure", "mmHg", 30.0..200.0, 70.0..100.0),
    HEART_RATE("Heart Rate", "bpm", 20.0..250.0, 60.0..100.0),
    HEIGHT("Height", "cm", 30.0..260.0, null),
    WEIGHT("Weight", "kg", 1.0..500.0, null),
    TEMPERATURE("Temperature", "°C", 30.0..45.0, 36.1..37.2),
    RESPIRATORY_RATE("Respiratory Rate", "breaths/min", 4.0..70.0, 12.0..20.0),
    SPO2("SpO₂ (Oxygen Saturation)", "%", 50.0..100.0, 95.0..100.0),
    PAIN_LEVEL("Pain Level", "/10", 0.0..10.0, 0.0..3.0);

    companion object {
        /** Vitals that make sense on a time-series chart (height rarely changes). */
        val chartable: List<VitalType> = entries.filter { it != HEIGHT }
    }
}
