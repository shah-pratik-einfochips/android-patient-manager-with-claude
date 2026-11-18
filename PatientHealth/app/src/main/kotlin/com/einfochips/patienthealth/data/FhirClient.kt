package com.einfochips.patienthealth.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

sealed class FhirException(message: String) : Exception(message) {
    class Http(val code: Int) : FhirException("The server responded with status $code.")
    class Decoding : FhirException("The server response could not be read.")
    class Network(cause: Throwable) : FhirException("Network error: ${cause.message ?: "unknown"}")
}

interface FhirApi {
    suspend fun upsertPatient(json: JsonObject): JsonObject
    suspend fun fetchPatient(id: String): JsonObject
    suspend fun createObservation(json: JsonObject): String
    suspend fun fetchObservations(patientId: String): List<JsonObject>
}

class FhirClient(
    private val baseUrl: String,
    private val http: OkHttpClient,
    private val maxRetries: Int = 3,
    private val retryDelayMs: Long = 500,
    private val tokenProvider: () -> String? = { null },
) : FhirApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/fhir+json".toMediaType()

    override suspend fun upsertPatient(json: JsonObject): JsonObject {
        val id = json["id"]?.jsonPrimitive?.content
        return if (id != null) call("Patient/$id", "PUT", json) else call("Patient", "POST", json)
    }

    override suspend fun fetchPatient(id: String): JsonObject = call("Patient/$id", "GET", null)

    override suspend fun createObservation(json: JsonObject): String =
        call("Observation", "POST", json)["id"]?.jsonPrimitive?.content ?: throw FhirException.Decoding()

    override suspend fun fetchObservations(patientId: String): List<JsonObject> {
        val bundle = call("Observation?patient=$patientId&category=vital-signs&_sort=-date&_count=100", "GET", null)
        return (bundle["entry"] as? JsonArray)?.mapNotNull { it.jsonObject["resource"] as? JsonObject }.orEmpty()
    }

    private suspend fun call(path: String, method: String, body: JsonObject?): JsonObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path).header("Accept", "application/fhir+json")
        tokenProvider()?.let { builder.header("Authorization", "Bearer $it") }
        builder.method(method, body?.let { json.encodeToString(JsonObject.serializer(), it).toRequestBody(mediaType) })
        val request = builder.build()

        var attempt = 0
        while (true) {
            try {
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        if (text.isBlank()) return@withContext JsonObject(emptyMap())
                        return@withContext runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { throw FhirException.Decoding() }
                    }
                    if (!isRetryable(response.code) || attempt >= maxRetries) throw FhirException.Http(response.code)
                }
            } catch (e: IOException) {
                if (attempt >= maxRetries) throw FhirException.Network(e)
            }
            attempt++
            delay(retryDelayMs * (1L shl (attempt - 1)))
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException("retry loop exited")
    }

    companion object {
        /** Retry only transient failures: timeouts, rate limiting and 5xx. */
        fun isRetryable(status: Int) = status == 408 || status == 429 || status in 500..599
    }
}
