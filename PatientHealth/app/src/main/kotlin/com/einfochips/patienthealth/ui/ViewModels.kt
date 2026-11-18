package com.einfochips.patienthealth.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.einfochips.patienthealth.data.DuplicateMedicalIdException
import com.einfochips.patienthealth.data.PatientRepository
import com.einfochips.patienthealth.domain.AuditEntry
import com.einfochips.patienthealth.domain.DashboardCalculator
import com.einfochips.patienthealth.domain.Patient
import com.einfochips.patienthealth.domain.PatientFormState
import com.einfochips.patienthealth.domain.VitalRecord
import com.einfochips.patienthealth.domain.VitalsEntryState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private fun <T> Flow<T>.whileSubscribed(scope: CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial)

@HiltViewModel
class PatientListViewModel @Inject constructor(repo: PatientRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    val patients: StateFlow<List<Patient>> = combine(repo.observePatients(), _query) { list, q ->
        if (q.isBlank()) list else list.filter { it.fullName.contains(q, true) || it.medicalId.contains(q, true) }
    }.whileSubscribed(viewModelScope, emptyList())
}

@HiltViewModel
class PatientFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: PatientRepository,
) : ViewModel() {
    private val patientId: String? = savedState["patientId"]
    private var original: Patient? = null

    private val _state = MutableStateFlow(PatientFormState(dob = LocalDate.now().minusYears(30)))
    val state: StateFlow<PatientFormState> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    val isEditing get() = patientId != null

    init {
        patientId?.let { id ->
            viewModelScope.launch { repo.getPatient(id)?.let { original = it; _state.value = PatientFormState.from(it) } }
        }
    }

    fun update(transform: (PatientFormState) -> PatientFormState) = _state.update(transform)

    fun save(onDone: () -> Unit) {
        _state.update { it.copy(submitted = true) }
        val s = _state.value
        if (!s.isValid) return
        viewModelScope.launch {
            try {
                repo.savePatient(s.toPatient(original), isNew = original == null)
                onDone()
            } catch (e: DuplicateMedicalIdException) {
                _error.value = e.message
            }
        }
    }
}

data class SyncUi(val syncing: Boolean = false, val message: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PatientDetailViewModel @Inject constructor(private val repo: PatientRepository) : ViewModel() {
    private val id = MutableStateFlow<String?>(null)

    val patient: StateFlow<Patient?> = id.flatMapLatest { it?.let(repo::observePatient) ?: flowOf(null) }
        .whileSubscribed(viewModelScope, null)

    /** Vitals from the past 7 days, oldest first. */
    val recentVitals: StateFlow<List<VitalRecord>> = id.flatMapLatest { pid ->
        pid?.let { repo.observeVitalsSince(it, DashboardCalculator.windowStart()) } ?: flowOf(emptyList())
    }.whileSubscribed(viewModelScope, emptyList())

    private val _entry = MutableStateFlow(VitalsEntryState())
    val entry: StateFlow<VitalsEntryState> = _entry.asStateFlow()
    private val _sync = MutableStateFlow(SyncUi())
    val sync: StateFlow<SyncUi> = _sync.asStateFlow()

    fun load(patientId: String) {
        if (id.value != patientId) { id.value = patientId; _entry.value = VitalsEntryState() }
    }

    fun updateEntry(transform: (VitalsEntryState) -> VitalsEntryState) = _entry.update(transform)

    fun saveVitals(onSaved: () -> Unit) {
        val pid = id.value ?: return
        val e = _entry.value
        if (!e.canSave) return
        viewModelScope.launch {
            repo.addVitals(e.toRecord(pid))
            _entry.value = VitalsEntryState()
            onSaved()
        }
    }

    fun syncNow() {
        val pid = id.value ?: return
        viewModelScope.launch {
            _sync.value = SyncUi(syncing = true)
            val message = try {
                repo.push(pid); repo.pull(pid); "Sync complete."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Sync failed."
            }
            _sync.value = SyncUi(false, message)
        }
    }

    fun messageShown() = _sync.update { it.copy(message = null) }

    fun delete(onDone: () -> Unit) {
        val pid = id.value ?: return
        viewModelScope.launch { repo.deletePatient(pid); onDone() }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(repo: PatientRepository) : ViewModel() {
    val audit: StateFlow<List<AuditEntry>> = repo.observeAudit(50).whileSubscribed(viewModelScope, emptyList())
}
