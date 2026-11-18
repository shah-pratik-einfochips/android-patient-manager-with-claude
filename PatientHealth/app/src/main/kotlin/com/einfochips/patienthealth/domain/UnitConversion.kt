package com.einfochips.patienthealth.domain

import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign
import java.util.Locale

object UnitConversion {
    private const val CM_PER_INCH = 2.54
    private const val LBS_PER_KG = 2.20462262185

    /** Converts feet and inches to centimeters. */
    fun cm(feet: Double, inches: Double): Double = (feet * 12 + inches) * CM_PER_INCH

    /** Splits centimeters into whole feet and remaining inches (one decimal), carrying 12" into the next foot. */
    fun feetAndInches(cm: Double): Pair<Int, Double> {
        val totalInches = cm / CM_PER_INCH
        var feet = (totalInches / 12).toInt()
        var inches = (totalInches - feet * 12).roundTo(1)
        if (inches >= 12) { feet += 1; inches = 0.0 }
        return feet to inches
    }

    fun kg(lbs: Double): Double = lbs / LBS_PER_KG
    fun lbs(kg: Double): Double = kg * LBS_PER_KG
    fun celsius(fahrenheit: Double): Double = (fahrenheit - 32) * 5 / 9
    fun fahrenheit(celsius: Double): Double = celsius * 9 / 5 + 32

    /** Mean arterial pressure: (SBP + 2 x DBP) / 3, rounded to one decimal. */
    fun meanArterialPressure(systolic: Double, diastolic: Double): Double = ((systolic + 2 * diastolic) / 3).roundTo(1)
}

/** Rounds half away from zero (clinical convention, matches the iOS app) to the given decimal places. */
fun Double.roundTo(places: Int): Double {
    val p = 10.0.pow(places)
    return sign * floor(abs(this) * p + 0.5) / p
}

/** One-decimal display text ("72.0"), locale-independent so screens, tests and exports agree. */
fun Double.oneDecimal(): String = "%.1f".format(Locale.US, this)
