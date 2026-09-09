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

import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.expression.EvaluationContext
import dev.ohs.fhir.workflow.expression.ProtocolExpression
import dev.ohs.player.reference.app.util.FhirJson
import health.hopena.cxca.cql.CqlExpressionEvaluator
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.ExperimentalResourceApi
import player_reference.reference_app.generated.resources.Res

/**
 * CQL-backed [ImmunizationDueEvaluator] (jvm/desktop only). Runs WHO's measles decision logic
 * verbatim on the cqframework v5 engine through cxca-cql, over CQL libraries, ValueSets, and
 * modelinfo bundled unmodified from `smart.who.int.immunizations` 0.2.0 under `files/cql/` (see
 * PROVENANCE.md). The evaluator is compiled once and cached.
 */
object CqlImmunizationDueEvaluator : ImmunizationDueEvaluator {

  private const val ENTRY_CQL = "files/cql/IMMZD2DTMeaslesLowTransmissionLogic.cql"
  private const val DEFINE = "Client is due for MCV1"
  // WHO's own recommendation text for the current state (a case expression over due/not-due).
  private const val GUIDANCE_DEFINE = "Guidance"

  // The entry library's transitive `include` closure + FHIRHelpers 4.0.1 (not in the WHO package).
  private val INCLUDED_CQL =
    listOf(
      "files/cql/IMMZD2DTMeaslesEncounterElements.cql",
      "files/cql/IMMZD2DTMeaslesElements.cql",
      "files/cql/IMMZEncounterElements.cql",
      "files/cql/IMMZElements.cql",
      "files/cql/IMMZCommon.cql",
      "files/cql/IMMZConcepts.cql",
      "files/cql/WHOEncounterElements.cql",
      "files/cql/WHOElements.cql",
      "files/cql/WHOCommon.cql",
      "files/cql/WHOConcepts.cql",
      "files/cql/FHIRHelpers.cql",
    )
  private val VALUE_SETS =
    listOf("files/cql/ValueSet-IMMZ.Z.DE9.json", "files/cql/ValueSet-IMMZ.Z.LiveAttenuated.json")
  private const val MODELINFO = "files/cql/fhir-modelinfo-4.0.1.xml"

  private var evaluator: CqlExpressionEvaluator? = null

  private suspend fun evaluator(): CqlExpressionEvaluator =
    evaluator
      ?: CqlExpressionEvaluator(
          cqlLibrarySource = text(ENTRY_CQL),
          valueSetJsons = VALUE_SETS.map { text(it) },
          modelInfoXml = text(MODELINFO),
          includedLibrarySources = INCLUDED_CQL.map { text(it) },
        )
        .also { evaluator = it }

  @OptIn(ExperimentalResourceApi::class)
  private suspend fun text(path: String): String = Res.readBytes(path).decodeToString()

  override suspend fun measlesMcv1Due(
    patient: Patient,
    immunizations: List<Immunization>,
    today: LocalDate,
  ): ImmunizationDue? {
    val context =
      EvaluationContext(
        subject = patient,
        // cxca-cql merges Bundle-typed variables into the engine's data; an empty list needs none.
        variables =
          if (immunizations.isEmpty()) emptyMap()
          else mapOf("immunizations" to collectionBundle(immunizations)),
        today = today,
      )
    // Single-library evaluator: no Expression.reference needed — its own entry library holds.
    val evaluator = evaluator()
    val result = evaluator.evaluate(ProtocolExpression.Elm(DEFINE), context)
    return when (result.asBoolean()) {
      true -> ImmunizationDue(label = "Measles (MCV1)", guidance = guidance(evaluator, context))
      false -> null
      null -> {
        // Fail safe: never render a "due" we could not compute — surface why in the log instead.
        println("ImmunizationDue: \"$DEFINE\" did not evaluate to a Boolean: $result")
        null
      }
    }
  }

  /** WHO's verbatim guidance string for the current state; null (no tooltip) if unavailable. */
  private suspend fun guidance(
    evaluator: CqlExpressionEvaluator,
    context: EvaluationContext,
  ): String? =
    evaluator.evaluate(ProtocolExpression.Elm(GUIDANCE_DEFINE), context).asValues().firstOrNull()
      as? String

  /** Immunizations as a collection Bundle (the shape cxca-cql folds into the engine's data). */
  private fun collectionBundle(immunizations: List<Immunization>): Bundle {
    val entries =
      immunizations.joinToString(",") {
        """{"resource":${FhirJson.instance.encodeToString(Resource.serializer(), it)}}"""
      }
    return FhirJson.instance.decodeFromString(
      Bundle.serializer(),
      """{"resourceType":"Bundle","type":"collection","entry":[$entries]}""",
    )
  }
}
