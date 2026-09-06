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
package dev.ohs.player.reference.app.feature.questionnaire

import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.RelatedPerson
import dev.ohs.player.reference.app.util.FhirJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.litlfred.fmlrunner.FmlRunner

/**
 * Probe: StructureMap-based extraction of QIMMZC4 via fmlrunner, executing the
 * bundled WHO IMMZ.C4 maps verbatim (same registration set as
 * [FmlExtractionService], read from the bundled files directly since compose
 * Res isn't available under jvmTest).
 *
 * Notably asserts sex→gender translation live — the path the template-extract
 * retrofit has to @Ignore (kotlin-fhir Enumeration drops primitive extensions),
 * FML extraction handles as WHO wrote it: translate(sex, IMMZ.C.ConceptMap).
 */
class QimmzC4FmlExtractionTest {

  private val fhirJson = FhirJson.instance
  private val fmlDir = File("src/commonMain/composeResources/files/fml")

  private val runner: FmlRunner by lazy {
    FmlRunner().also { r ->
      listOf(
          "IMMZ.C4.QRToPatient.fml",
          "IMMZ.C4.QRToLM.fml",
          "IMMZ.C4.LMToPatient.fml",
          "IMMZ.Helpers.fml",
        )
        .forEach {
        val compiled = r.compileFml(File(fmlDir, it).readText())
        r.registerStructureMap(
          compiled.structureMap ?: error("$it failed to compile: ${compiled.errors.firstOrNull()}")
        )
      }
      r.registerConceptMap(File(fmlDir, "ConceptMap-IMMZ.C.ConceptMap.json").readText())
      r.registerStructureDefinition(File(fmlDir, "StructureDefinition-IMMZC4.json").readText())
    }
  }

  private val responseJson =
    """
    {
      "resourceType": "QuestionnaireResponse",
      "questionnaire": "http://smart.who.int/immunizations/Questionnaire/QIMMZC4",
      "status": "completed",
      "item": [
        { "linkId": "uniqueIdentifier", "answer": [{ "valueString": "ID-12345" }] },
        { "linkId": "name", "answer": [{ "valueString": "Amina Okoro" }] },
        { "linkId": "firstName", "answer": [{ "valueString": "Amina" }] },
        { "linkId": "familyName", "answer": [{ "valueString": "Okoro" }] },
        {
          "linkId": "sex",
          "answer": [
            {
              "valueCoding": {
                "system": "http://smart.who.int/immunizations/CodeSystem/IMMZ.C",
                "code": "DE7",
                "display": "Female"
              }
            }
          ]
        },
        { "linkId": "dateOfBirth", "answer": [{ "valueDate": "2024-02-29" }] },
        {
          "linkId": "caregiversMultiple",
          "item": [
            { "linkId": "caregiversFullName", "answer": [{ "valueString": "Grace Okoro" }] },
            { "linkId": "caregiversFirstName", "answer": [{ "valueString": "Grace" }] },
            { "linkId": "caregiversFamilyName", "answer": [{ "valueString": "Okoro" }] }
          ]
        },
        { "linkId": "contactPhoneNumber", "answer": [{ "valueString": "+256700000001" }] },
        { "linkId": "address", "answer": [{ "valueString": "Plot 5, Kololo, Kampala" }] }
      ]
    }
    """
      .trimIndent()

  private fun extract(json: String = responseJson): Bundle {
    val result =
      runner.executeStructureMap(FmlExtractionService.IMMZ_C4_MAP_URL, json)
    check(result.success && result.result != null) {
      "FML extraction failed: ${result.errors.firstOrNull()}"
    }
    return fhirJson.decodeFromString(Bundle.serializer(), result.result!!)
  }

  @Test
  fun extract_producesPatientPerWhoMaps() {
    val patient =
      assertNotNull(
        extract().entry.mapNotNull { it.resource as? Patient }.singleOrNull(),
        "expected exactly one Patient",
      )

    // Verbatim fidelity includes upstream defects: WHO's published C4 maps
    // never write uniqueIdentifier into Patient.identifier
    // (smart-immunizations#138; fix PR #139). Flip this to a value assert
    // when #139 merges and the bundled maps are refreshed.
    assertEquals(emptyList(), patient.identifier)
    val name = patient.name.single()
    assertEquals("Amina Okoro", name.text?.value)
    assertEquals("Amina", name.given.single().value)
    assertEquals("Okoro", name.family?.value)
    assertEquals("2024-02-29", patient.birthDate?.value?.toString())
    assertEquals("+256700000001", patient.telecom.single().value?.value)
    assertEquals("Plot 5, Kololo, Kampala", patient.address.single().text?.value)
  }

  @Test
  fun extract_translatesSexToGender() {
    // translate(sex, IMMZ.C.ConceptMap, 'code'): DE7 -> female — live via FML,
    // where the template retrofit's equivalent stays @Ignore'd upstream.
    val patient = extract().entry.mapNotNull { it.resource as? Patient }.single()
    assertEquals("female", patient.gender?.value?.getCode())
  }

  @Test
  fun extract_producesRelatedPersonReferencingPatient() {
    val bundle = extract()
    val relatedPerson =
      assertNotNull(
        bundle.entry.mapNotNull { it.resource as? RelatedPerson }.singleOrNull(),
        "expected exactly one RelatedPerson for one caregiver group instance",
      )

    val name = relatedPerson.name.single()
    assertEquals("Grace Okoro", name.text?.value)
    assertEquals("Grace", name.given.single().value)
    assertEquals("Okoro", name.family?.value)

    val patientId = bundle.entry.mapNotNull { it.resource as? Patient }.single().id
    assertEquals("Patient/$patientId", relatedPerson.patient?.reference?.value)
  }

  @Test
  fun extract_omitsGenderWhenSexUnanswered() {
    val withoutSex =
      responseJson.replace(Regex("\\{\\s*\"linkId\": \"sex\"[\\s\\S]*?\\]\\s*\\},"), "")
    val patient = extract(withoutSex).entry.mapNotNull { it.resource as? Patient }.single()
    assertEquals(null, patient.gender)
  }
}
