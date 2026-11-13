NOTE: This is a read-only file. Do not modify it.

# Patient Health Management Module - Android Application

## Overview
A native Android application for phones and tablets that enables healthcare staff and patients to manage patient demographic data and daily vitals, storing them securely in a FHIR-compliant backend with visualization and reporting capabilities.

## Scope
Develop an Android application that allows healthcare staff or patients to manage patient demographic data and daily vitals, storing them securely in a FHIR-compliant backend with visualization and reporting capabilities.

All tasks below are planned; completion must be verified during implementation.

## Features

### 1. Patient Information Management
- [ ] Register and manage patient profiles with comprehensive demographic data:
  - First name, last name, date of birth, gender, ethnicity
  - Contact information (phone, email)
  - Medical ID, blood type, allergies, medications, health conditions
- [ ] Validate demographic fields, including mandatory ID and dates of birth not in the future.
- [ ] Map demographics to FHIR Patient resources and clinical details to appropriate linked FHIR resources.
- [ ] Implement data privacy controls and consent management to support applicable HIPAA/GDPR obligations.
- [ ] Track examination purpose, type, performed by, and referral by.
- [ ] Provide a dropdown for type of examination with these options: Routine checkup, Emergency, Follow-up, Consultation, Diagnostic, Preventive.

### 2. Vitals Entry
- [ ] Implement vitals input and unit conversions:
  - Blood pressure: systolic, diastolic, and auto-calculated MAP
  - Heart rate (bpm)
  - Height (cm / ft and in)
  - Weight (lbs / kg)
  - Temperature (C / F)
  - Respiratory rate (breaths/min)
  - SpO2 (blood oxygen saturation %)
  - Pain level (0-10 scale)
  - Symptoms (free text)
- [ ] Provide real-time validation against clinically approved ranges.
- [ ] Auto-fill the current date with an option to select past dates.
- [ ] Show abnormal-value indicators without relying on color alone.

### 3. Vitals Dashboard
- [ ] Visualize the past seven days of vitals.
- [ ] Provide tabular and interactive line-chart views.
- [ ] Highlight abnormal values with accessible indicators.
- [ ] Implement CSV and PDF exports with secure temporary file handling and Android sharing support.
- [ ] Filter charts by vital type and display detailed vital statistics.

### 4. FHIR Integration
- [ ] Store demographics as FHIR Patient resources.
- [ ] Store vitals as FHIR Observation resources linked to the patient, with appropriate codes and units.
- [ ] Implement asynchronous API operations using Kotlin coroutines.
- [ ] Retrieve and update patient data and vitals history.
- [ ] Support Development, Staging, and Production environments.
- [ ] Handle authentication, authorization, errors, and bounded retries without duplicate submissions.
- [ ] Implement offline handling and background sync with WorkManager and explicit conflict resolution.

### 5. Accessibility & Compliance
- [ ] Apply WCAG accessibility guidance and verify contrast ratios.
- [ ] Support TalkBack, meaningful semantics, labels, and traversal order.
- [ ] Support system font scaling and adequate touch target sizes.
- [ ] Clearly label clinical terms, input errors, and accessibility hints.
- [ ] Encrypt sensitive local data; Room alone does not provide database encryption.
- [ ] Implement privacy-safe audit logging and consent management.
- [ ] Review HIPAA/GDPR obligations across the app, backend, and operational processes.

## Technical Architecture

### Design Pattern: MVVM with Unidirectional Data Flow
Separate Compose UI, ViewModels, domain logic, and data repositories; expose lifecycle-aware UI state through StateFlow.

### Technology Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose and Material 3
- **State and concurrency**: ViewModel, StateFlow, Kotlin coroutines
- **Dependency injection**: Hilt
- **Persistence**: Room with a maintained, compatible encryption solution
- **Networking**: Retrofit/OkHttp with Kotlin serialization
- **Navigation**: Navigation Compose
- **Charts**: Evaluate a maintained Compose-compatible chart library for accessibility and licensing
- **Background work**: WorkManager
- **Minimum Android**: Define based on device support and dependency requirements
- **Target SDK**: Meet current distribution requirements

## Code Quality Standards
- [ ] Follow Kotlin and Android coding conventions with separation of concerns.
- [ ] Use immutable UI state and lifecycle-aware state collection.
- [ ] Prevent memory leaks and release resources appropriately.
- [ ] Keep networking and disk operations off the main thread.
- [ ] Implement comprehensive validation and null safety.
- [ ] Run Android Lint, detekt, and ktlint.
- [ ] Provide accessible reusable UI components.
- [ ] Complete PDF export, background sync, and localization.
- [ ] Optimize layouts for tablets, rotation, and multi-window operation.

## Unit Testing Automation
Following requirements.md Unit Testing Automation section:
- [ ] Configure JUnit, coroutine test utilities, and AndroidX Test.
- [ ] Test validation, patient models, vitals models, and unit conversions with positive and negative cases.
- [ ] Test repositories, API failures, retries, and offline sync.
- [ ] Test ViewModel state transitions and lifecycle behavior.
- [ ] Add Compose UI, screenshot, and accessibility tests.
- [ ] Test Room queries and schema migrations.
- [ ] Generate coverage reports using JaCoCo or Kover; establish coverage targets rather than claiming unmeasured results.
- [ ] Document pass/fail reports and integrate verification into CI/CD.

## Security & Privacy
- [ ] Encrypt sensitive data at rest and use secure TLS for all network communication.
- [ ] Protect encryption keys using Android Keystore-backed key management.
- [ ] Store tokens securely and never embed backend secrets in the application.
- [ ] Implement patient consent, access controls, and session expiration.
- [ ] Exclude sensitive data from logs, backups, crash reports, and exported files unless explicitly authorized.
- [ ] Assess HIPAA/GDPR compliance with appropriate security and legal review.

## Migration & Compatibility
Following requirements.md Migration & Compatibility section:
- [ ] Use Kotlin for new code and assess Java migration where applicable.
- [ ] Maintain compatible stable Kotlin, Gradle, Android Gradle Plugin, and library versions.
- [ ] Define and document supported minimum and target API levels.
- [ ] Track database versions and implement tested Room migrations.
- [ ] Validate data integrity and document recovery strategies for failed upgrades.
- [ ] Maintain a compatibility matrix across Android versions and device manufacturers.
- [ ] Verify phones, tablets, portrait, landscape, and multi-window layouts.

## Supported Devices
- Android phones and tablets within the agreed API support range
- Portrait and landscape orientations
- Adaptive layouts for different screen sizes and multi-window use
