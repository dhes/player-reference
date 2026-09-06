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

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.litlfred.fmlrunner.FmlRunner
import player_reference.reference_app.generated.resources.Res

/**
 * StructureMap-based extraction of WHO smart-immunizations questionnaires via
 * fmlrunner — the extraction mechanism the published guideline itself declares
 * (Questionnaire targetStructureMap), executing WHO's .fml verbatim.
 *
 * The maps, the IMMZC4 logical model, and the sex ConceptMap are bundled
 * unmodified from the published IG (CC0) under files/fml/.
 */
object FmlExtractionService {

  const val IMMZ_C4_MAP_URL = "http://smart.who.int/immunizations/StructureMap/IMMZ.C4.QRToPatient"

  private val FML_MAPS =
    listOf(
      "files/fml/IMMZ.C4.QRToPatient.fml",
      "files/fml/IMMZ.C4.QRToLM.fml",
      "files/fml/IMMZ.C4.LMToPatient.fml",
      "files/fml/IMMZ.Helpers.fml",
    )
  private val CONCEPT_MAPS = listOf("files/fml/ConceptMap-IMMZ.C.ConceptMap.json")
  private val STRUCTURE_DEFINITIONS = listOf("files/fml/StructureDefinition-IMMZC4.json")

  private var runner: FmlRunner? = null

  @OptIn(ExperimentalResourceApi::class)
  private suspend fun runner(): FmlRunner =
    runner
      ?: FmlRunner()
        .also { r ->
          FML_MAPS.forEach { path ->
            val compiled = r.compileFml(Res.readBytes(path).decodeToString())
            val map =
              compiled.structureMap
                ?: error("Bundled map '$path' failed to compile: ${compiled.errors.firstOrNull()}")
            r.registerStructureMap(map)
          }
          CONCEPT_MAPS.forEach { r.registerConceptMap(Res.readBytes(it).decodeToString()) }
          STRUCTURE_DEFINITIONS.forEach {
            r.registerStructureDefinition(Res.readBytes(it).decodeToString())
          }
        }
        .also { runner = it }

  /**
   * Runs the QR through [IMMZ_C4_MAP_URL] and returns the produced Bundle as a
   * JSON string. Throws with the engine's error when execution fails — nothing
   * fails silently.
   */
  suspend fun extractImmzC4(questionnaireResponseJson: String): String {
    val result = runner().executeStructureMap(IMMZ_C4_MAP_URL, questionnaireResponseJson)
    if (!result.success || result.result == null) {
      error("FML extraction failed: ${result.errors.firstOrNull() ?: "no output"}")
    }
    return result.result!!
  }
}
