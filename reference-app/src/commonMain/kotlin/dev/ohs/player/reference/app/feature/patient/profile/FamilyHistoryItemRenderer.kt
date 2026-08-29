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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.ohs.player.client.renderer.ComponentRenderer
import dev.ohs.player.client.renderer.RenderOptions
import dev.ohs.player.generated.config.FamilyHistoryItemConfig
import dev.ohs.player.generated.state.PatientFamilyHistoryState
import dev.ohs.player.reference.app.feature.component.common.StatusRow
import org.jetbrains.compose.resources.stringResource
import player_reference.reference_app.generated.resources.Res
import player_reference.reference_app.generated.resources.family_history_onset_age
import player_reference.reference_app.generated.resources.family_history_unknown

class FamilyHistoryItemRenderer :
  ComponentRenderer<PatientFamilyHistoryState, FamilyHistoryItemConfig> {
  @Composable
  override fun Render(
    item: PatientFamilyHistoryState,
    config: FamilyHistoryItemConfig,
    options: RenderOptions,
  ) {
    val relation = item.relationName?.replaceFirstChar { it.uppercaseChar() }
    // FHIRPath toString() renders decimals in scientific notation ("7.8E+1"); normalize.
    val onsetAge =
      item.fmhOnsetAge?.let { raw ->
        raw.toDoubleOrNull()?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
          ?: raw
      }
    val onset = onsetAge?.let { stringResource(Res.string.family_history_onset_age, it) }
    val outcome = if (config.showOutcome != false) item.fmhOutcome else null
    StatusRow(
      title = item.fmhConditionName ?: stringResource(Res.string.family_history_unknown),
      modifier = options.modifier,
      subtitle = listOfNotNull(relation, onset, outcome).joinToString(" · ").ifEmpty { null },
      accentColor = MaterialTheme.colorScheme.outline,
      status = null,
    )
  }
}
