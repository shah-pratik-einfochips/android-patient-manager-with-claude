package com.einfochips.patienthealth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.einfochips.patienthealth.data.PatientRepository
import com.einfochips.patienthealth.domain.VitalRecord
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Seeds seven days of varied vitals (including abnormal ones) for the first patient. Debug builds only. */
@AndroidEntryPoint
class DebugSeedReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: PatientRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i("DebugSeed", "start")
                val patient = repo.observePatients().first().firstOrNull() ?: run { Log.w("DebugSeed", "no patient"); return@launch }
                val day = 86_400_000L
                val now = System.currentTimeMillis()
                val hr = listOf(72.0, 85.0, 130.0, 95.0, 58.0, 76.0, 101.0)
                val sys = listOf(118.0, 124.0, 150.0, 132.0, 112.0, 119.0, 141.0)
                val dia = listOf(76.0, 80.0, 95.0, 84.0, 70.0, 77.0, 90.0)
                val temp = listOf(36.6, 36.8, 38.4, 37.0, 36.4, 36.7, 37.6)
                val spo2 = listOf(98.0, 97.0, 92.0, 96.0, 99.0, 98.0, 94.0)
                for (i in 0..6) {
                    repo.addVitals(VitalRecord(
                        patientId = patient.id, recordedAt = now - i * day - 60_000, systolic = sys[i], diastolic = dia[i],
                        heartRate = hr[i], temperatureC = temp[i], respiratoryRate = 14.0 + i, spo2 = spo2[i], painLevel = (i % 5).toDouble(),
                        symptoms = if (i == 2) "fever, chills \"mild\"" else "",
                    ))
                }
                Log.i("DebugSeed", "seeded 7 records for ${patient.id}")
            } catch (e: Exception) {
                Log.e("DebugSeed", "failed", e)
            } finally { pending.finish() }
        }
    }
}
