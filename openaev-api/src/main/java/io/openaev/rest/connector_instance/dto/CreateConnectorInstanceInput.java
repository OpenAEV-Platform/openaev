package io.openaev.rest.connector_instance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateConnectorInstanceInput {

  @Getter
  @Setter
  public static class ConfigurationInput {
    @Schema(description = "Configuration key")
    @JsonProperty("configuration_key")
    @NotBlank
    private String key;

    @Schema(description = "Configuration value")
    @JsonProperty("configuration_value")
    private JsonNode value;
  }

  @JsonProperty("catalog_connector_id")
  @NotBlank
  private String catalogConnectorId;

  @JsonProperty("connector_instance_configurations")
  private List<ConfigurationInput> configurations = new ArrayList<>();
}
