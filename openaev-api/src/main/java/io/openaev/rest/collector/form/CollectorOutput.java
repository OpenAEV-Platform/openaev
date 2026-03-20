package io.openaev.rest.collector.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.connector.dto.ConnectorOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Schema(description = "Collector output")
public class CollectorOutput extends ConnectorOutput {

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

  @JsonProperty("existing_collector")
  private boolean existing;
}
