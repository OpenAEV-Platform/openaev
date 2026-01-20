package io.openaev.rest.injector.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorSimpleOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.Builder;

@Builder
@Schema(description = "Injector output")
public class InjectorOutput {
  @Schema(description = "Injector id")
  @JsonProperty("injector_id")
  @NotBlank
  private String id;

  @JsonProperty("injector_name")
  @NotBlank
  private String name;

  @JsonProperty("injector_type")
  @NotBlank
  private String type;

  @JsonProperty("injector_external")
  private boolean external = false;

  @JsonProperty("catalog")
  private CatalogConnectorSimpleOutput catalog;

  @JsonProperty("is_verified")
  private boolean verified = false;

  @JsonProperty("current_status")
  private ConnectorInstance.CURRENT_STATUS_TYPE currentStatus;

  @JsonProperty("existing_injector")
  private boolean existing;

  @JsonProperty("injector_updated_at")
  private Instant updatedAt;
}
