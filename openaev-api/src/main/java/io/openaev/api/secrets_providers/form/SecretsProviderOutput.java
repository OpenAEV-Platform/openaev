package io.openaev.api.secrets_providers.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.connector.dto.ConnectorOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Schema(description = "Secrets provider output")
public class SecretsProviderOutput extends ConnectorOutput {
  @Schema(description = "Secrets provider id")
  @JsonProperty("secrets_provider_id")
  @NotBlank
  private String id;

  @JsonProperty("secrets_provider_name")
  @NotBlank
  private String name;

  @JsonProperty("secrets_provider_type")
  @NotBlank
  private String type;

  @JsonProperty("existing_secret_provider")
  private boolean existing;
}
