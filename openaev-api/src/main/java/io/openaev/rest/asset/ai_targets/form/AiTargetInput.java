package io.openaev.rest.asset.ai_targets.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCriticality;
import io.openaev.rest.asset.form.AssetInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AiTargetInput extends AssetInput {

  // Property names match the Asset AI fields (aiTarget*) so setUpdateAttributes'
  // BeanUtils.copyProperties maps them onto the Asset entity; @JsonProperty keeps the wire names.

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("ai_target_provider")
  private Asset.AI_TARGET_PROVIDER aiTargetProvider;

  @JsonProperty("ai_target_endpoint")
  @Schema(types = {"string", "null"})
  private String aiTargetEndpoint;

  @JsonProperty("ai_target_model")
  @Schema(types = {"string", "null"})
  private String aiTargetModel;

  @JsonProperty("ai_target_modality")
  private Asset.AI_TARGET_MODALITY aiTargetModality = Asset.AI_TARGET_MODALITY.TEXT;

  @JsonProperty("ai_target_system_prompt")
  @Schema(types = {"string", "null"})
  private String aiTargetSystemPrompt;

  @JsonProperty("ai_target_configuration")
  private Map<String, Object> aiTargetConfiguration = new HashMap<>();

  @JsonProperty("ai_target_token")
  @Schema(types = {"string", "null"})
  private String aiTargetToken;

  // Criticality lives on the base Asset entity; AI targets are Assets so they support it too.
  // Property name matches Asset.criticality for BeanUtils.copyProperties.
  @JsonProperty("asset_criticality")
  @Schema(types = {"string", "null"})
  private AssetCriticality criticality;
}
