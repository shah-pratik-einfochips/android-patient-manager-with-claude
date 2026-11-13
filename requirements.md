NOTE: This is a read-only file. Do not modify it.

## Requirement Gathering
* Collaborate with stakeholders to capture functional and non-functional requirements.
* Prepare user stories, acceptance criteria, and feature backlog.
* Conduct feasibility analysis for Android platform limitations (e.g., background execution, runtime permissions, push notifications, and offline sync).
* Collect performance and security expectations to be considered in the architecture phase.
* Create a requirement traceability matrix to map business needs with app features.

## Design & Documentation
* Design application architecture (MVVM with unidirectional data flow or Clean Architecture) considering scalability.
* Create UI/UX wireframes, flow diagrams, and navigation architecture using Material Design guidelines.
* Define data flow diagrams, sequence diagrams, and class structures.
* Document technical decisions including frameworks, third-party dependencies, and libraries.
* Provide developer onboarding documentation with coding standards and project guidelines.

## Application Setup & Code Generation
* Create a new Android application using Android Studio, Kotlin, and Jetpack Compose with a modularized structure.
* Define product flavors and build types for multiple environments (Dev, QA, Prod).
* Configure Gradle Kotlin DSL and version catalogs for dependency management.
* Configure Room data models, entity relationships, and database migrations; select a maintained encryption solution for sensitive local data.
* Implement initial navigation using Navigation Compose.
* Configure dependency injection using Hilt and asynchronous operations using Kotlin coroutines and Flow.
* Set up app signing for development and distribution, keeping signing credentials outside source control.
* Implement code generation using Kotlin Symbol Processing where required by selected libraries.
* Establish coding guidelines and reusable templates to accelerate feature development.

## Code Analysis, Refactoring & Enhancement
* Perform Android Lint, detekt, and ktlint analysis for code quality and maintainability.
* Refactor duplicate or legacy code into reusable modules.
* Review memory leaks and resource usage using Android Studio Profiler and LeakCanary in debug builds.
* Suggest modularization ensuring scalability and backward compatibility.
* Apply coding standards to improve readability and long-term maintainability.

## Unit Testing Automation
* Configure JUnit for local unit tests and AndroidX Test for instrumented tests.
* Develop positive and negative test cases for critical modules, including coroutine and Flow behavior.
* Implement Compose UI and screenshot testing for UI consistency.
* Analyze code coverage and generate reports using JaCoCo or Kover.
* Document test cases with pass/fail reports for the QA team.

## Migration & Compatibility
* Migrate Java code to Kotlin and XML-based UI to Jetpack Compose where applicable.
* Select a supported minimum Android API level and target the API level required for distribution.
* Upgrade Kotlin, Android Gradle Plugin, Gradle, and third-party libraries to compatible stable versions.
* Implement and test Room schema migrations without losing patient data.
* Validate compatibility across Android versions, phones, tablets, screen sizes, and device manufacturers.

## Consulting – SOLID Principles & Best Practices
* Review the codebase for SOLID violations and recommend modular refactoring.
* Suggest dependency injection, structured concurrency, and lifecycle-aware Flow collection.
* Recommend reusable UI components using Jetpack Compose and Material Design.
* Document coding standards for scalable Android projects.
* Propose guidelines for error handling, privacy-safe logging, and crash reporting.

## Analysis of Potential Performance Issues
* Identify bottlenecks using Android Studio Profiler, Perfetto, and Macrobenchmark.
* Optimize Room queries, indexes, pagination, and transaction usage.
* Prevent blocking I/O and expensive computation on the main thread.
* Minimize unnecessary Compose recompositions and optimize scrolling lists.
* Implement image caching if required.
* Reduce app launch time by lazy-loading heavy modules and evaluating Baseline Profiles.
* Use WorkManager for deferrable persistent background sync while respecting battery and execution limits.
