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
import dev.ohs.player.generated.config.ProcedureItemConfig
import dev.ohs.player.generated.state.PatientProcedureState
import dev.ohs.player.reference.app.feature.component.common.StatusChipData
import dev.ohs.player.reference.app.feature.component.common.StatusRow
import org.jetbrains.compose.resources.stringResource
import player_reference.reference_app.generated.resources.Res
import player_reference.reference_app.generated.resources.procedure_performed
import player_reference.reference_app.generated.resources.procedure_unknown

class ProcedureItemRenderer : ComponentRenderer<PatientProcedureState, ProcedureItemConfig> {
  @Composable
  override fun Render(
    item: PatientProcedureState,
    config: ProcedureItemConfig,
    options: RenderOptions,
  ) {
    val isCompleted = item.procedureStatus?.lowercase() == "completed"
    StatusRow(
      title =
        item.procedureName ?: item.procedureText ?: stringResource(Res.string.procedure_unknown),
      modifier = options.modifier,
      subtitle =
        if (config.showDate != false)
          item.performedDate?.let { stringResource(Res.string.procedure_performed, it) }
        else null,
      accentColor =
        if (isCompleted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
      status =
        if (config.showStatus != false)
          item.procedureStatus?.let {
            val (bg, fg) =
              when (it.lowercase()) {
                "completed" ->
                  MaterialTheme.colorScheme.tertiaryContainer to
                    MaterialTheme.colorScheme.onTertiaryContainer
                "not-done",
                "stopped" ->
                  MaterialTheme.colorScheme.errorContainer to
                    MaterialTheme.colorScheme.onErrorContainer
                else ->
                  MaterialTheme.colorScheme.surfaceVariant to
                    MaterialTheme.colorScheme.onSurfaceVariant
              }
            StatusChipData(it.replaceFirstChar { c -> c.uppercaseChar() }, bg, fg)
          }
        else null,
    )
  }
}
