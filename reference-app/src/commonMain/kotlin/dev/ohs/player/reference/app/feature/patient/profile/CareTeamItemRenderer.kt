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
import dev.ohs.player.generated.config.CareTeamItemConfig
import dev.ohs.player.generated.state.PatientCareTeamState
import dev.ohs.player.reference.app.feature.component.common.StatusRow
import org.jetbrains.compose.resources.stringResource
import player_reference.reference_app.generated.resources.Res
import player_reference.reference_app.generated.resources.care_team_unknown

class CareTeamItemRenderer : ComponentRenderer<PatientCareTeamState, CareTeamItemConfig> {
  @Composable
  override fun Render(
    item: PatientCareTeamState,
    config: CareTeamItemConfig,
    options: RenderOptions,
  ) {
    StatusRow(
      title = item.memberName ?: stringResource(Res.string.care_team_unknown),
      modifier = options.modifier,
      subtitle = if (config.showRole != false) item.roleName else null,
      accentColor = MaterialTheme.colorScheme.primary,
      status = null,
    )
  }
}
