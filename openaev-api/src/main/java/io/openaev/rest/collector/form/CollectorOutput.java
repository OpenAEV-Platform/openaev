package io.openaev.rest.collector.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorSimpleOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.Builder;

@Builder
@Schema(description = "Collector output")
public class CollectorOutput {

  @Schema(description = "Collector id")
  @JsonProperty("collector_id")
  @NotBlank
  private String id;

  @JsonProperty("collector_name")
  @NotBlank
  private String name;

  @JsonProperty("collector_type")
  @NotBlank
  private String type;

  @JsonProperty("collector_external")
  private boolean external = false;

  @JsonProperty("collector_last_execution")
  private Instant lastExecution;

  @JsonProperty("catalog")
  private CatalogConnectorSimpleOutput catalog;

  @JsonProperty("current_status")
  private ConnectorInstance.CURRENT_STATUS_TYPE currentStatus;

  @JsonProperty("existing_collector")
  private boolean existing;

  @JsonProperty("is_verified")
  private boolean verified = false;
}
