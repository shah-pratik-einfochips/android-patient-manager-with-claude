package com.einfochips.patienthealth

import com.einfochips.patienthealth.data.*
import com.einfochips.patienthealth.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class FhirMapperTest {
    private fun patient() = Patient(medicalId = "MRN9", firstName = "Ann", lastName = "Lee", dobEpochDay = LocalDate.of(1990, 5, 17).toEpochDay(),
        gender = Gender.FEMALE, phone = "5551234567", email = "a@b.co", bloodType = BloodType.O_POS)

    @Test fun patientResource() {
        val j = FhirMapper.patientResource(patient())
        assertEquals("Patient", j["resourceType"]!!.jsonPrimitive.content)
        assertEquals("female", j["gender"]!!.jsonPrimitive.content)
        assertEquals("1990-05-17", j["birthDate"]!!.jsonPrimitive.content)
        assertEquals(2, j["telecom"]!!.jsonArray.size); assertNull(j["id"])
        assertEquals("Lee", j["name"]!!.jsonArray[0].jsonObject["family"]!!.jsonPrimitive.content)
    }
    @Test fun omitsEmptyTelecomAndIncludesId() {
        val j = FhirMapper.patientResource(patient().copy(phone = "", email = "", fhirId = "42"))
        assertEquals(0, j["telecom"]!!.jsonArray.size); assertEquals("42", j["id"]!!.jsonPrimitive.content)
    }
    @Test fun applyPatient() {
        val json = Json.parseToJsonElement("""{"id":"7","name":[{"family":"Kim","given":["Joy"]}],"gender":"male","birthDate":"1980-01-02","telecom":[{"system":"phone","value":"999"}]}""").jsonObject
        val p = FhirMapper.applyPatient(json, patient())
        assertEquals("7", p.fhirId); assertEquals("Joy Kim", p.fullName); assertEquals(Gender.MALE, p.gender)
        assertEquals(LocalDate.of(1980, 1, 2).toEpochDay(), p.dobEpochDay); assertEquals("999", p.phone)
    }
    private fun rec(vararg f: Pair<String, Double>): VitalRecord {
        val m = f.toMap()
        return VitalRecord(patientId = "p", recordedAt = 1_700_000_000_000, systolic = m["sys"], diastolic = m["dia"], heartRate = m["hr"], heightCm = m["h"],
            weightKg = m["w"], temperatureC = m["t"], respiratoryRate = m["rr"], spo2 = m["o2"], painLevel = m["pain"])
    }
    @Test fun bpPanelAndMap() {
        val obs = FhirMapper.observations(rec("sys" to 120.0, "dia" to 80.0, "hr" to 70.0), "P1")
        assertEquals(3, obs.size); assertEquals(2, obs[0]["component"]!!.jsonArray.size)
        assertEquals("Patient/P1", obs[0]["subject"]!!.jsonObject["reference"]!!.jsonPrimitive.content)
    }
    @Test fun onlyRecordedValues() {
        assertEquals(0, FhirMapper.observations(rec(), "P").size)
        assertEquals(0, FhirMapper.observations(rec("sys" to 120.0), "P").size)
        assertEquals(2, FhirMapper.observations(rec("o2" to 97.0, "pain" to 0.0), "P").size)
    }
    @Test fun fullRecord() = assertEquals(9, FhirMapper.observations(
        rec("sys" to 120.0, "dia" to 80.0, "hr" to 70.0, "h" to 170.0, "w" to 65.0, "t" to 36.8, "rr" to 16.0, "o2" to 98.0, "pain" to 1.0), "P").size)
    @Test fun roundTrip() {
        val src = rec("sys" to 118.0, "dia" to 76.0, "hr" to 66.0, "h" to 171.0, "w" to 70.0, "t" to 36.6, "rr" to 15.0, "o2" to 99.0, "pain" to 2.0)
        val back = FhirMapper.vitalRecord(FhirMapper.observations(src, "P"), "p", src.recordedAt)!!
        assertEquals(118.0, back.systolic!!, 0.0); assertEquals(76.0, back.diastolic!!, 0.0); assertEquals(66.0, back.heartRate!!, 0.0)
        assertEquals(171.0, back.heightCm!!, 0.0); assertEquals(70.0, back.weightKg!!, 0.0); assertEquals(36.6, back.temperatureC!!, 0.0)
        assertEquals(15.0, back.respiratoryRate!!, 0.0); assertEquals(99.0, back.spo2!!, 0.0); assertEquals(2.0, back.painLevel!!, 0.0)
    }
    @Test fun unknownObservationsNull() {
        assertNull(FhirMapper.vitalRecord(listOf(Json.parseToJsonElement("""{"code":{"coding":[{"code":"0000-0"}]}}""").jsonObject), "p", 0))
        assertNull(FhirMapper.vitalRecord(emptyList(), "p", 0))
    }
}

/** OkHttp client that answers from a scripted list of responses/exceptions, recording requests. */
class ScriptedHttp(private val script: MutableList<Any>) {
    val requests = mutableListOf<Request>()
    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        requests += chain.request()
        val next = if (script.isEmpty()) 200 to "{}" else script.removeAt(0)
        if (next is IOException) throw next
        @Suppress("UNCHECKED_CAST") val pair = next as Pair<Int, String>
        val (code, body) = pair
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/fhir+json".toMediaType())).build()
    }.build()
}

class FhirClientTest {
    private fun client(h: ScriptedHttp, retries: Int = 2, token: String? = null) =
        FhirClient("https://x.test/r4/", h.client, retries, 1, { token })

    @Test fun postWhenNoId() = runTest {
        val h = ScriptedHttp(mutableListOf(201 to """{"id":"1"}"""))
        assertEquals("1", client(h).upsertPatient(buildJsonObject { put("resourceType", "Patient") })["id"]!!.jsonPrimitive.content)
        assertEquals("POST", h.requests[0].method); assertEquals("https://x.test/r4/Patient", h.requests[0].url.toString())
        assertTrue(h.requests[0].body!!.contentType().toString().startsWith("application/fhir+json"))
    }
    @Test fun putWhenHasId() = runTest {
        val h = ScriptedHttp(mutableListOf(200 to """{"id":"5"}"""))
        client(h).upsertPatient(buildJsonObject { put("id", "5") })
        assertEquals("PUT", h.requests[0].method); assertEquals("/r4/Patient/5", h.requests[0].url.encodedPath)
    }
    @Test fun bearerToken() = runTest {
        val h = ScriptedHttp(mutableListOf(200 to """{"id":"1"}""")); client(h, token = "tok").fetchPatient("1")
        assertEquals("Bearer tok", h.requests[0].header("Authorization"))
    }
    @Test fun retriesOn503ThenSucceeds() = runTest {
        val h = ScriptedHttp(mutableListOf(503 to "{}", 503 to "{}", 200 to """{"id":"9"}"""))
        assertEquals("9", client(h).fetchPatient("9")["id"]!!.jsonPrimitive.content); assertEquals(3, h.requests.size)
    }
    @Test fun givesUpAfterMaxRetries() = runTest {
        val h = ScriptedHttp(MutableList(10) { 500 to "{}" })
        try { client(h, retries = 2).fetchPatient("1"); fail() } catch (e: FhirException.Http) { assertEquals(500, e.code); assertEquals(3, h.requests.size) }
    }
    @Test fun noRetryOn404() = runTest {
        val h = ScriptedHttp(mutableListOf(404 to "{}"))
        try { client(h).fetchPatient("1"); fail() } catch (e: FhirException.Http) { assertEquals(404, e.code); assertEquals(1, h.requests.size) }
    }
    @Test fun retriesOnNetworkError() = runTest {
        val h = ScriptedHttp(mutableListOf(IOException("offline"), 200 to """{"id":"1"}"""))
        client(h).fetchPatient("1"); assertEquals(2, h.requests.size)
    }
    @Test fun networkErrorSurfacesAfterRetries() = runTest {
        val h = ScriptedHttp(MutableList(10) { IOException("offline") })
        try { client(h, retries = 1).fetchPatient("1"); fail() } catch (e: FhirException.Network) { assertEquals(2, h.requests.size) }
    }
    @Test fun invalidJsonIsDecodingError() = runTest {
        try { client(ScriptedHttp(mutableListOf(200 to "not json"))).fetchPatient("1"); fail() } catch (e: FhirException.Decoding) { }
    }
    @Test fun createObservationId() = runTest { assertEquals("o1", client(ScriptedHttp(mutableListOf(201 to """{"id":"o1"}"""))).createObservation(JsonObject(emptyMap()))) }
    @Test fun createObservationMissingId() = runTest {
        try { client(ScriptedHttp(mutableListOf(201 to "{}"))).createObservation(JsonObject(emptyMap())); fail() } catch (e: FhirException.Decoding) { }
    }
    @Test fun bundleParsing() = runTest {
        val h = ScriptedHttp(mutableListOf(200 to """{"entry":[{"resource":{"id":"a"}},{"resource":{"id":"b"}}]}"""))
        assertEquals(2, client(h).fetchObservations("P").size); assertTrue(h.requests[0].url.toString().contains("Observation?patient=P"))
    }
    @Test fun emptyBundle() = runTest { assertTrue(client(ScriptedHttp(mutableListOf(200 to """{"resourceType":"Bundle"}"""))).fetchObservations("P").isEmpty()) }
    @Test fun retryableStatuses() {
        listOf(408, 429, 500, 502, 503, 599).forEach { assertTrue("$it", FhirClient.isRetryable(it)) }
        listOf(200, 400, 401, 403, 404, 422).forEach { assertFalse("$it", FhirClient.isRetryable(it)) }
    }
}

// region Repository with in-memory fakes

class FakePatientDao : PatientDao {
    val rows = MutableStateFlow<List<Patient>>(emptyList())
    override fun observeAll(): Flow<List<Patient>> = rows
    override fun observe(id: String): Flow<Patient?> = rows.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun get(id: String) = rows.value.firstOrNull { it.id == id }
    override suspend fun consented() = rows.value.filter { it.consentGiven }
    override suspend fun countDuplicates(medicalId: String, excludingId: String) = rows.value.count { it.medicalId == medicalId && it.id != excludingId }
    override suspend fun upsert(patient: Patient) { rows.value = rows.value.filter { it.id != patient.id } + patient }
    override suspend fun delete(id: String) { rows.value = rows.value.filter { it.id != id } }
}
class FakeVitalDao : VitalDao {
    val rows = MutableStateFlow<List<VitalRecord>>(emptyList())
    override fun observeSince(patientId: String, since: Long) = rows.map { l -> l.filter { it.patientId == patientId && it.recordedAt >= since } }
    override suspend fun all(patientId: String) = rows.value.filter { it.patientId == patientId }
    override suspend fun unsynced(patientId: String) = rows.value.filter { it.patientId == patientId && it.fhirIds.isEmpty() }
    override suspend fun countNear(patientId: String, time: Long) = rows.value.count { it.patientId == patientId && kotlin.math.abs(it.recordedAt - time) < 1000 }
    override suspend fun insert(record: VitalRecord) { rows.value = rows.value.filter { it.id != record.id } + record }
    override suspend fun setFhirIds(id: String, ids: String) { rows.value = rows.value.map { if (it.id == id) it.copy(fhirIds = ids) else it } }
}
class FakeAuditDao : AuditDao {
    val rows = MutableStateFlow<List<AuditEntry>>(emptyList())
    override suspend fun insert(entry: AuditEntry) { rows.value = rows.value + entry }
    override fun observeRecent(limit: Int) = rows.map { it.takeLast(limit) }
    override fun observeCount() = rows.map { it.size }
}
class FakeFhir : FhirApi {
    var created = mutableListOf<JsonObject>(); var remote = listOf<JsonObject>(); var failUpsert = false
    override suspend fun upsertPatient(json: JsonObject): JsonObject { if (failUpsert) throw FhirException.Http(500); return buildJsonObject { put("id", "srv1") } }
    override suspend fun fetchPatient(id: String) = Json.parseToJsonElement("""{"id":"$id","name":[{"family":"Remote","given":["Pat"]}]}""").jsonObject
    override suspend fun createObservation(json: JsonObject): String { created += json; return "o${created.size}" }
    override suspend fun fetchObservations(patientId: String) = remote
}

class PatientRepositoryTest {
    private val pd = FakePatientDao(); private val vd = FakeVitalDao(); private val ad = FakeAuditDao(); private val api = FakeFhir()
    private val repo = PatientRepository(pd, vd, ad, api)
    private fun patient(consent: Boolean = true, id: String = "p1", mid: String = "M1") =
        Patient(id = id, medicalId = mid, firstName = "A", lastName = "B", dobEpochDay = 0, consentGiven = consent)

    @Test fun saveRejectsDuplicateMedicalId() = runTest {
        repo.savePatient(patient(), true)
        try { repo.savePatient(patient(id = "p2"), true); fail() } catch (e: DuplicateMedicalIdException) { }
        repo.savePatient(patient().copy(firstName = "Edited"), false)
        assertEquals(1, pd.rows.value.size)
    }
    @Test fun mutationsAreAudited() = runTest {
        repo.savePatient(patient(), true); repo.addVitals(VitalRecord(patientId = "p1", recordedAt = 1, heartRate = 70.0)); repo.deletePatient("p1")
        assertEquals(listOf("patient.create", "vitals.create", "patient.delete"), ad.rows.value.map { it.action })
        assertTrue("audit detail holds ids only", ad.rows.value.none { it.detail.contains("70") })
    }
    @Test fun pushRequiresConsent() = runTest {
        pd.upsert(patient(consent = false))
        try { repo.push("p1"); fail() } catch (e: ConsentRequiredException) { }
        assertNull(pd.rows.value[0].fhirId)
    }
    @Test fun pushUploadsOnce() = runTest {
        pd.upsert(patient()); vd.insert(VitalRecord(patientId = "p1", recordedAt = 1, heartRate = 70.0))
        repo.push("p1")
        assertEquals("srv1", pd.rows.value[0].fhirId); assertEquals("o1", vd.rows.value[0].fhirIds)
        repo.push("p1"); assertEquals(1, api.created.size)
    }
    @Test fun pullNeedsPriorSync() = runTest { pd.upsert(patient()); try { repo.pull("p1"); fail() } catch (e: NotSyncedException) { } }
    @Test fun pullMergesWithoutDuplicates() = runTest {
        pd.upsert(patient().copy(fhirId = "srv1"))
        val when_ = 1_700_000_000_000
        api.remote = FhirMapper.observations(VitalRecord(patientId = "p1", recordedAt = when_, heartRate = 88.0), "srv1").mapIndexed { i, o ->
            JsonObject(o + ("id" to JsonPrimitive("r$i")))
        }
        repo.pull("p1"); repo.pull("p1")
        assertEquals(1, vd.rows.value.size); assertEquals(88.0, vd.rows.value[0].heartRate!!, 0.0)
        assertEquals("Remote", pd.rows.value[0].lastName)
    }
    @Test fun pushAllCountsFailures() = runTest {
        pd.upsert(patient()); pd.upsert(patient(consent = false, id = "p2", mid = "M2"))
        assertEquals(0, repo.pushAllConsented())
        api.failUpsert = true; assertEquals(1, repo.pushAllConsented())
    }
}

// endregion

class ExportServiceTest {
    private fun r(t: Long = 0, hr: Double? = null, s: String = "") = VitalRecord(patientId = "p", recordedAt = t, heartRate = hr, symptoms = s)
    @Test fun csvHeaderAndRow() {
        val lines = ExportService.csv(listOf(r(hr = 72.0, s = "Cough"))).lines()
        assertEquals(2, lines.size); assertTrue(lines[0].startsWith("Date,Systolic BP (mmHg)"))
        assertTrue(lines[1].contains("72.0")); assertTrue(lines[1].endsWith("Cough"))
    }
    @Test fun csvEscapes() = assertTrue(ExportService.csv(listOf(r(s = "a, \"b\"\nc"))).contains("\"a, \"\"b\"\"\nc\""))
    @Test fun csvSortedAscending() {
        val rows = ExportService.csv(listOf(r(200, 2.0), r(100, 1.0))).lines()
        assertTrue(rows[1].contains("1.0")); assertTrue(rows[2].contains("2.0"))
    }
    @Test fun csvEmpty() = assertEquals(1, ExportService.csv(emptyList()).lines().size)
}
