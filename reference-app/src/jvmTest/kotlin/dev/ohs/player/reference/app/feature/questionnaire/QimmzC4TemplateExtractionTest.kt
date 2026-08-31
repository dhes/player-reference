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

import dev.ohs.fhir.datacapture.extraction.template.TemplateExtractionEngine
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.RelatedPerson
import dev.ohs.player.reference.app.util.FhirJson
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Probe: verifies the template-extraction retrofit of WHO smart-immunizations QIMMZC4
 * against the behavior specified by IMMZ.C4.LMToPatient.fml (Patient fields, sex-to-gender
 * translation, one RelatedPerson per caregiver group referencing the Patient).
 */
class QimmzC4TemplateExtractionTest {

  private val fhirJson = FhirJson.instance

  private val questionnaire: Questionnaire by lazy {
    val json =
      File("src/commonMain/composeResources/files/configs/Questionnaire-QIMMZC4.json").readText()
    fhirJson.decodeFromString(Questionnaire.serializer(), json)
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

  private fun extract() =
    TemplateExtractionEngine.extract(
      questionnaire,
      fhirJson.decodeFromString(QuestionnaireResponse.serializer(), responseJson),
    )

  @Test
  fun retrofit_declaresTemplateExtraction() {
    assertTrue(TemplateExtractionEngine.canExtract(questionnaire))
  }

  @Test
  fun extract_producesPatientMatchingFmlFieldRules() {
    val bundle = extract()
    val patient =
      assertNotNull(
        bundle.entry.mapNotNull { it.resource as? Patient }.singleOrNull(),
        "expected exactly one Patient",
      )

    assertEquals("ID-12345", patient.identifier.single().value?.value)
    val name = patient.name.single()
    assertEquals("Amina Okoro", name.text?.value)
    assertEquals("Amina", name.given.single().value)
    assertEquals("Okoro", name.family?.value)
    assertEquals("2024-02-29", patient.birthDate?.value?.toString())
    assertEquals("+256700000001", patient.telecom.single().value?.value)
    assertEquals("Plot 5, Kololo, Kampala", patient.address.single().text?.value)
  }

  /**
   * Documents an upstream blocker, not a template limitation: kotlin-fhir's Enumeration type
   * drops primitive extensions on enum-bound fields at parse time, so the `_gender`
   * templateExtractValue hole never reaches the extraction engine (verified by round-trip:
   * `_gender` disappears on decode/encode while `_birthDate` survives). Un-ignore once the
   * model preserves Element.id/extension on Enumeration primitives.
   */
  @Ignore
  @Test
  fun extract_translatesSexToGender_blockedByEnumerationExtensionLoss() {
    val patient = extract().entry.mapNotNull { it.resource as? Patient }.single()
    // Frozen projection of translate(sex, IMMZ.C.ConceptMap, 'code'): DE7 -> female
    assertEquals("female", patient.gender?.value?.getCode())
  }

  @Test
  fun extract_producesRelatedPersonPerCaregiverReferencingPatient() {
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

    val patientFullUrl =
      bundle.entry.first { it.resource is Patient }.fullUrl?.value
    assertEquals(
      patientFullUrl,
      relatedPerson.patient?.reference?.value,
      "RelatedPerson.patient should hold the Patient entry's fullUrl as a local reference",
    )
  }

  @Test
  fun extract_omitsGenderWhenSexUnanswered() {
    val withoutSex =
      responseJson.replace(Regex("\\{\\s*\"linkId\": \"sex\"[\\s\\S]*?\\]\\s*\\},"), "")
    val bundle =
      TemplateExtractionEngine.extract(
        questionnaire,
        fhirJson.decodeFromString(QuestionnaireResponse.serializer(), withoutSex),
      )
    val patient = bundle.entry.mapNotNull { it.resource as? Patient }.single()
    assertEquals(null, patient.gender)
  }
}
