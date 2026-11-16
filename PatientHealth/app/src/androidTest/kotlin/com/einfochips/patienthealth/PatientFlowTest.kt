package com.einfochips.patienthealth

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.hasContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.einfochips.patienthealth.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flow on a device or emulator: register a patient, record an abnormal reading,
 * open the dashboard. Runs against the real app graph, so it uses a unique medical ID per run
 * instead of assuming a clean database.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PatientFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun field(label: String) = rule.onNode(hasSetTextAction() and hasText(label))

    @Test
    fun registerPatientRecordVitalsAndViewDashboard() {
        val medicalId = "MRN-UI-${System.currentTimeMillis()}"

        rule.onNodeWithContentDescription("Add patient").performClick()
        field("Medical ID").performTextInput(medicalId)
        field("First name").performTextInput("Maya")
        field("Last name").performTextInput("Patel")
        rule.onNodeWithText("Pick").performScrollTo().performClick()
        rule.onNodeWithText("OK").performClick()
        rule.onNodeWithText("Save").performClick()

        // Back on the list, the new patient is shown; open it.
        rule.waitUntilAtLeastOneExists(hasText("ID $medicalId"), 5_000)
        rule.onNodeWithText("ID $medicalId").performClick()

        // Record vitals with an abnormal heart rate.
        rule.onNodeWithText("Enter Vitals").performClick()
        field("Heart rate (bpm)").performScrollTo().performTextInput("130")
        field("Systolic (mmHg)").performScrollTo().performTextInput("120")
        field("Diastolic (mmHg)").performScrollTo().performTextInput("80")
        // The abnormal state must be exposed as text and an icon description, not colour alone.
        rule.waitUntilAtLeastOneExists(hasText("Outside normal range"), 3_000)
        rule.waitUntilAtLeastOneExists(hasContentDescription("Abnormal value"), 3_000)
        rule.onNodeWithText("Save Vitals").performScrollTo().performClick()

        // Saving switches to the dashboard.
        rule.waitUntilAtLeastOneExists(hasText("Table"), 5_000)
        rule.onNodeWithText("Table").performClick()
        rule.waitUntilAtLeastOneExists(hasText("130", substring = true), 3_000)
    }
}
