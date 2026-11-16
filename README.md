# Patient Health (Android)

Native Android app for patient demographics and daily vitals. Data is stored in an encrypted local database, uploaded to a FHIR R4 server for consented patients, and shown on a 7-day dashboard with CSV and PDF export. iOS and Flutter versions live in separate repositories. The Gradle project is in `PatientHealth/`.

## Stack
Kotlin, Jetpack Compose (Material 3), Hilt, Room on SQLCipher with a Keystore-wrapped key, WorkManager, OkHttp, kotlinx.serialization, Navigation Compose, Kover. minSdk 26, targetSdk 36.

## Layout (`PatientHealth/app/src/main/kotlin/com/einfochips/patienthealth`)
- `domain` – enums, models, validation, unit conversion, form state, dashboard maths
- `data` – database, key provider, FHIR mapper and client, repository, export
- `work` – 6-hourly background sync
- `ui` – screens and view models
- `app/src/debug` – debug-only demo data seeder

## Flavors
| Flavor | Application ID suffix | FHIR base URL |
|---|---|---|
| dev | `.dev` | `http://10.0.2.2:8080/fhir/` (emulator to host machine; cleartext allowed only here) |
| qa | `.qa` | `https://qa.fhir.example.com/r4/` (placeholder) |
| prod | none | `https://fhir.example.com/r4/` (placeholder) |

Replace the qa and prod URLs before use.

## Build and test
Use JDK 17 or later (Android Studio's bundled JDK works).
```bash
cd PatientHealth
./gradlew testDevDebugUnitTest
./gradlew assembleDevDebug
```
Release signing reads `keystore.properties`, which is git-ignored. Keep keystores and credentials out of the repository.

## Security and privacy
- Room database is encrypted with SQLCipher; the passphrase is wrapped by the Android Keystore.
- Uploads happen only for patients who gave consent.
- The audit log records actions and IDs only, never clinical values.

## Known gaps
No Compose UI or instrumented tests, no migration tests, no localization, no lint run, release signing not exercised.

## Instrumented tests and CI

`app/src/androidTest` holds a Compose UI test (`PatientFlowTest`) that registers a patient, records an abnormal reading and opens the dashboard on a device or emulator:

```bash
cd PatientHealth && ./gradlew connectedDevDebugAndroidTest
```

GitHub Actions (`.github/workflows/ci.yml`) runs the unit tests and a debug build, then the instrumented test on an API 34 emulator, for every push to `main` and every pull request.
