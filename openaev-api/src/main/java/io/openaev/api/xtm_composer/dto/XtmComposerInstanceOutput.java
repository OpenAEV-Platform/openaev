package io.openaev.api.xtm_composer.dto;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConnectorInstance;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Builder;

@Builder
@JsonInclude(NON_NULL)
public class XtmComposerInstanceOutput {
  @Builder
  public static class Configuration {
    @Schema(description = "Configuration key")
    @JsonProperty("configuration_key")
    @NotBlank
    private String key;

    @Schema(description = "Configuration value")
    @JsonProperty("configuration_value")
    private String value;

    @Schema(description = "Configuration is encrypted")
    @JsonProperty("configuration_is_encrypted")
    private boolean isEncrypted;
  }

  @Schema(description = "Connector Instance Id")
  @JsonProperty("connector_instance_id")
  @NotBlank
  private String id;

  @Schema(description = "Connector Instance name")
  @JsonProperty("connector_instance_name")
  @NotBlank
  private String name;

  @Schema(description = "Connector Instance hash")
  @JsonProperty("connector_instance_hash")
  @NotBlank
  private String hash;

  @Schema(description = "Connector image")
  @JsonProperty("connector_image")
  @NotBlank
  private String image;

  @Schema(description = "Connector Instance current status")
  @JsonProperty("connector_instance_current_status")
  @NotBlank
  private ConnectorInstance.CURRENT_STATUS_TYPE currentStatus;

  @Schema(description = "Connector Instance requested status")
  @JsonProperty("connector_instance_requested_status")
  @NotBlank
  private ConnectorInstance.REQUESTED_STATUS_TYPE requestedStatus;

  @Schema(description = "Connector Instance configuration")
  @JsonProperty("connector_instance_configurations")
  @NotBlank
  private List<Configuration> configurations;
}
