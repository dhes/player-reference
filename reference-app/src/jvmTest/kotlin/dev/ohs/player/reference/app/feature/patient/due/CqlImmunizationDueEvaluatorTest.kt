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
import dev.ohs.player.reference.app.util.FhirJson
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

/**
 * Exercises the verbatim CQL due-chip end to end: the cqframework v5 engine runs WHO's
 * `IMMZD2DTMeaslesLowTransmissionLogic."Client is due for MCV1"` over content bundled from
 * `smart.who.int.immunizations` 0.2.0. Also the runtime proof of the antlr-kotlin diamond
 * resolution (F21): fhir-path deserializes these fixtures under the forced 1.0.3 while cql-to-elm
 * compiles the libraries on the same classpath.
 */
class CqlImmunizationDueEvaluatorTest {

  // Fixed evaluation clock: Mary (born 2025-09-07) is 12 months + 1 day old on this date.
  private val today = LocalDate(2026, 9, 8)

  private fun patient(json: String) = FhirJson.instance.decodeFromString(Patient.serializer(), json)

  private fun immunization(json: String) =
    FhirJson.instance.decodeFromString(Immunization.serializer(), json)

  private val mary =
    patient("""{"resourceType":"Patient","id":"mary","gender":"female","birthDate":"2025-09-07"}""")

  @Test
  fun maryIsDueForMcv1() =
    runBlocking<Unit> {
      val due = CqlImmunizationDueEvaluator.measlesMcv1Due(mary, emptyList(), today)
      assertNotNull(due, "a 12-month-old with no measles dose should be due for MCV1")
      assertEquals("Measles (MCV1)", due.label)
      // WHO's verbatim Guidance define drives the tooltip.
      assertNotNull(due.guidance, "the due chip should carry WHO's guidance text")
      assertContains(due.guidance, "Should vaccinate client with MCV1")
    }

  @Test
  fun infantUnder12MonthsIsNotDue() =
    runBlocking<Unit> {
      val infant =
        patient(
          """{"resourceType":"Patient","id":"infant","gender":"male","birthDate":"2026-06-01"}"""
        )
      assertNull(
        CqlImmunizationDueEvaluator.measlesMcv1Due(infant, emptyList(), today),
        "a ~3-month-old is below the 12-month MCV1 age and must not be due",
      )
    }

  @Test
  fun nonMeaslesImmunizationDoesNotSuppressDue() =
    runBlocking<Unit> {
      // Exercises the immunizations Bundle path: an old, non-measles dose must not count toward the
      // measles primary series nor trip the recent-live-vaccine check, so MCV1 stays due.
      val bcg =
        immunization(
          """{"resourceType":"Immunization","id":"bcg","status":"completed",""" +
            """"vaccineCode":{"coding":[{"system":"http://snomed.info/sct","code":"420538001"}]},""" +
            """"patient":{"reference":"Patient/mary"},"occurrenceDateTime":"2025-10-01"}"""
        )
      val due = CqlImmunizationDueEvaluator.measlesMcv1Due(mary, listOf(bcg), today)
      assertNotNull(due, "a non-measles dose must not suppress the MCV1 recommendation")
    }
}
