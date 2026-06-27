package io.openaev.rest.asset.ai_targets.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AiTarget;
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

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("ai_target_provider")
  private AiTarget.AI_TARGET_PROVIDER provider;

  @JsonProperty("ai_target_endpoint")
  @Schema(types = {"string", "null"})
  private String endpoint;

  @JsonProperty("ai_target_model")
  @Schema(types = {"string", "null"})
  private String model;

  @JsonProperty("ai_target_modality")
  private AiTarget.AI_TARGET_MODALITY modality = AiTarget.AI_TARGET_MODALITY.TEXT;

  @JsonProperty("ai_target_system_prompt")
  @Schema(types = {"string", "null"})
  private String systemPrompt;

  @JsonProperty("ai_target_configuration")
  private Map<String, Object> configuration = new HashMap<>();

  @JsonProperty("ai_target_api_key_variable")
  @Schema(types = {"string", "null"})
  private String apiKeyVariable;
}
