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
package dev.ohs.player.reference.app.feature.patient.due

import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.fhir.model.r4.Patient
import kotlinx.datetime.LocalDate

/** A "due" recommendation surfaced as a chip on the patient profile. */
data class ImmunizationDue(val label: String, val guidance: String? = null)

/**
 * Decides whether a patient is due for a vaccine by executing WHO smart-immunizations decision
 * logic **verbatim**. Only implemented where a CQL engine is available (jvm/desktop for this
 * slice); the ViewModel injects it optionally, so the chip simply does not appear on targets
 * without an implementation.
 */
interface ImmunizationDueEvaluator {
  /**
   * Runs `IMMZD2DTMeaslesLowTransmissionLogic."Client is due for MCV1"` for [patient], given its
   * [immunizations], as of [today]. Returns the recommendation when due, or `null` when not due or
   * not evaluable — a computation we could not complete is never rendered as a false "due".
   */
  suspend fun measlesMcv1Due(
    patient: Patient,
    immunizations: List<Immunization>,
    today: LocalDate,
  ): ImmunizationDue?
}
