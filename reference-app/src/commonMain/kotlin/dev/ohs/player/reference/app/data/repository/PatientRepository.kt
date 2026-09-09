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
package dev.ohs.player.reference.app.data.repository

import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.fhir.model.r4.MedicationStatement
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Procedure
import dev.ohs.player.generated.state.AllergyReactionState
import dev.ohs.player.generated.state.PatientAllergyState
import dev.ohs.player.generated.state.PatientCareTeamState
import dev.ohs.player.generated.state.PatientConditionState
import dev.ohs.player.generated.state.PatientContactState
import dev.ohs.player.generated.state.PatientFamilyHistoryState
import dev.ohs.player.generated.state.PatientImmunizationState
import dev.ohs.player.generated.state.PatientMedicationState
import dev.ohs.player.generated.state.PatientProcedureState
import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.player.generated.state.PatientTelecomState
import dev.ohs.player.reference.app.data.Extraction.extractor
import dev.ohs.player.reference.app.data.datasource.allPatientIds
import dev.ohs.player.reference.app.data.datasource.patientProfileSearchResult
import dev.ohs.player.reference.app.data.datasource.patientSummarySearchResult
import dev.ohs.player.reference.app.feature.patient.profile.ProfileUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PatientRepository(private val fhirRepository: FhirRepository) {

  // FhirPathEvaluator holds mutable state is not concurrent-safe.
  // limitedParallelism(1) serializes all extraction on a single background thread without any
  // explicit locking.
  private val extractorDispatcher = Dispatchers.Default.limitedParallelism(1)

  fun observePatients(): Flow<List<PatientSummaryState>> =
    fhirRepository.revision.map { getPatients() }

  suspend fun getPatients(): List<PatientSummaryState> =
    withContext(extractorDispatcher) {
      allPatientIds(fhirRepository).mapNotNull { id ->
        patientSummarySearchResult(id, fhirRepository)?.let {
          extractor.extract<PatientSummaryState>(it).firstOrNull()
        }
      }
    }

  fun observePatientProfile(patientId: String): Flow<ProfileUiState> =
    fhirRepository.revision.map { getPatientProfile(patientId) }

  suspend fun getPatientProfile(patientId: String): ProfileUiState =
    withContext(extractorDispatcher) {
      val result =
        patientProfileSearchResult(patientId, fhirRepository) ?: return@withContext ProfileUiState()
      ProfileUiState(
        patient = extractor.extract<PatientSummaryState>(result).firstOrNull(),
        allergies = extractor.extract<PatientAllergyState>(result),
        allergyReactions = extractor.extract<AllergyReactionState>(result),
        medications =
          extractor
            .extract<PatientMedicationState>(result)
            // WORKAROUND for kotlin-fhirpath: paths into Dosage evaluate to empty (the
            // `is BackboneElement` dispatch arm shadows `is Dosage`), so the ViewDefinition's
            // dosage column never populates. Fill it from the raw resources until fixed upstream.
            .let { states ->
              val sigById =
                result.revIncluded
                  .orEmpty()
                  .values
                  .flatten()
                  .filterIsInstance<MedicationStatement>()
                  .associate { it.id to it.dosage.firstOrNull()?.text?.value }
              states.map { s ->
                if (s.dosage == null) s.copy(dosage = sigById[s.medicationId]) else s
              }
            }
            .sortedBy { it.medStatus != "active" },
        conditions =
          extractor.extract<PatientConditionState>(result).sortedBy {
            it.conditionStatus != "active"
          },
        immunizations =
          extractor.extract<PatientImmunizationState>(result).sortedByDescending {
            // ISO-8601 string form sorts chronologically; undated entries sink to the bottom.
            it.occurrenceDate?.toString() ?: ""
          },
        procedures =
          extractor
            .extract<PatientProcedureState>(result)
            // WORKAROUND for kotlin-fhirpath: the bare `performed` choice path evaluates to
            // empty (same shorthand issue as Immunization.occurrence), so the ViewDefinition's
            // performedDate column never populates. Fill it from the raw resources until fixed
            // upstream; Period-valued procedures use the period start.
            .let { states ->
              val dateById =
                result.revIncluded
                  .orEmpty()
                  .values
                  .flatten()
                  .filterIsInstance<Procedure>()
                  .associate { p ->
                    p.id to
                      (p.performed?.asDateTime()?.value?.value
                        ?: p.performed?.asPeriod()?.value?.start?.value)
                  }
              states.map { s ->
                if (s.performedDate == null) s.copy(performedDate = dateById[s.procedureId]) else s
              }
            }
            .sortedByDescending { it.performedDate?.toString() ?: "" },
        familyHistory = extractor.extract<PatientFamilyHistoryState>(result),
        careTeam = extractor.extract<PatientCareTeamState>(result),
        contacts =
          extractor.extract<PatientContactState>(result).filter {
            it.contactGivenName != null || it.contactFamilyName != null
          },
        telecoms = extractor.extract<PatientTelecomState>(result).filter { it.telecomValue != null },
      )
    }

  /**
   * Raw Patient plus its Immunizations, for decision-support (CQL) evaluation — the model, not the
   * rendered view states [getPatientProfile] returns. Null when the patient is absent.
   */
  suspend fun patientWithImmunizations(patientId: String): Pair<Patient, List<Immunization>>? {
    val patient = fhirRepository.get("Patient", patientId) as? Patient ?: return null
    val immunizations =
      fhirRepository.all("Immunization").filterIsInstance<Immunization>().filter {
        it.patient.reference?.value?.substringAfterLast('/') == patientId
      }
    return patient to immunizations
  }
}
