/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.player.reference.app.feature.patient.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ohs.player.reference.app.data.repository.PatientRepository
import dev.ohs.player.reference.app.feature.patient.due.ImmunizationDue
import dev.ohs.player.reference.app.feature.patient.due.ImmunizationDueEvaluator
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class PatientProfileViewModel(
  patientId: String,
  patientRepository: PatientRepository,
  // Optional: bound only where a CQL engine exists (jvm/desktop). Null elsewhere → no chip.
  immunizationDueEvaluator: ImmunizationDueEvaluator? = null,
) : ViewModel() {
  private val _uiState = MutableStateFlow<ProfileUiState?>(null)
  val uiState: StateFlow<ProfileUiState?> = _uiState.asStateFlow()

  private val _due = MutableStateFlow<ImmunizationDue?>(null)
  val due: StateFlow<ImmunizationDue?> = _due.asStateFlow()

  init {
    viewModelScope.launch {
      patientRepository.observePatientProfile(patientId).collect { _uiState.value = it }
    }
    if (immunizationDueEvaluator != null) {
      viewModelScope.launch {
        val (patient, immunizations) =
          patientRepository.patientWithImmunizations(patientId) ?: return@launch
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        _due.value = immunizationDueEvaluator.measlesMcv1Due(patient, immunizations, today)
      }
    }
  }
}
